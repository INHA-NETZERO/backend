# Zero-Waste Copilot — Spring 백엔드 설계 (브레인스토밍 산출 스펙)

> 팀 **Zero:Wave** · 작성일 2026-06-26 · 검증기준일 2026-06-26
> 제품 배경: [`../../idea_note.md`](../../idea_note.md) · 기술 상세 스펙: [`../../backend_spec.md`](../../backend_spec.md)
> 본 문서는 brainstorming 단계의 **검증된 설계(spec)** 이다. 다음 단계는 writing-plans(구현 계획).

---

## 1. 목표와 범위

POS·날씨·요일 데이터를 결합해 **익일 카테고리별 최적 발주량**을 산출하고, 과발주로 발생했을 폐기량을 **공인 배출계수 기반 절감 탄소(kgCO₂eq)** 로 환산해 점주에게 제공한다. 모든 정량 수치는 결정적 로직으로 산출하고, 자연어 설명만 sLLM에 위임한다(수치 환각 차단·추론 전력 최소화 — idea_note §3).

### 1.1 시스템 경계 (이번 브레인스토밍에서 확정)

| 계층                         | 책임                                                                                          | 기술                         | 소유          |
| ---------------------------- | --------------------------------------------------------------------------------------------- | ---------------------------- | ------------- |
| ① 프론트엔드                 | 대시보드·챗봇 UI                                                                              | React/Vue + Amplify          | 별도 레포     |
| **② Spring 백엔드(본 레포)** | ETL · 피처조립 · **발주최적화** · **탄소산정** · 영속화 · API · 오케스트레이션 · AI 서버 호출 | Spring Boot 3.5.13 / Java 21 | **본 레포**   |
| ③ **Python AI 서버**         | **LightGBM 수요예측 추론** + **sLLM 자연어**(예측 폴백/베이스라인 포함)                       | Python FastAPI               | **별도 레포** |

**결정 사항:**

1. **경계:** ML 추론(LightGBM)과 sLLM만 AI 서버로 분리. 신문팔이 발주최적화·탄소산정은 **Spring 내부 결정적 로직**으로 유지.
2. **연동:** AI 서버는 **상태 없는 추론 API**. Spring이 DB에서 피처·날씨를 조립해 전송, AI는 분위예측/자연어만 반환(DB 접근 없음).
3. **챗 경로:** 프론트 → Spring → AI 서버. Spring이 RAG 근거(예측·발주·탄소)를 DB에서 조립해 주입.
4. **예측 폴백:** LightGBM 미준비/실패 시 **AI 서버**가 단순 베이스라인 반환. Spring은 자체 예측 폴백을 보유하지 않는다(순수 결정적 유지).
5. **아키텍처:** 모듈러 모놀리식 + 얇은 포트(외부 경계만 인터페이스화).
6. **개발 수준:** **Demo(not Prod)** — 단일 매장 가정, 합성 CSV, 수동 트리거 위주. Prod 관심사(멀티테넌시·HA·정교한 권한)는 §11 이행과제.
7. **발주 단위:** 단일일이 아닌 **커버기간(리드타임+발주주기) 합산** 발주. OrderPolicy의 주기/리드타임을 예측 요청에 전달(예: 7일 주기 → 7일치 합산).

### 1.2 비목표 (YAGNI)

- Spring 내 ML 학습·추론, 자연어 생성, 자체 예측 폴백 로직.
- 이벤트소싱/아웃박스/CQRS(하루 1회 배치엔 과함).
- 멀티테넌시·수평확장·고가용 등 Prod 관심사(Demo 범위 밖).
- UI 렌더링.

---

## 2. 기술 스택 (최신 검증값, 2026-06-26)

| 구분         | 선택                                                           | 비고                                   |
| ------------ | -------------------------------------------------------------- | -------------------------------------- |
| 런타임       | Java 21 (LTS)                                                  | 현 Gradle toolchain                    |
| 프레임워크   | **Spring Boot 3.5.13**                                         | 현 build.gradle 3.5.3 → 패치 업        |
| Web          | spring-boot-starter-web (동기 MVC)                             | WebFlux 미채택                         |
| 영속성       | spring-data-jpa + **AWS RDS (MySQL 8)** (H2 MySQL 모드 테스트) |                                        |
| 마이그레이션 | Flyway                                                         | 스키마/시드                            |
| 외부 HTTP    | **RestClient**(AI 서버) + **`@HttpExchange`**(기상청)          | webflux 의존성 없음                    |
| 검증         | spring-boot-starter-validation                                 |                                        |
| 문서         | **springdoc-openapi-starter-webmvc-ui 2.8.17**                 | `/swagger-ui.html`                     |
| 관측         | actuator + Micrometer                                          | 저전력 계측                            |
| 회복탄력성   | resilience4j 2.2.0                                             | AI/기상청 timeout·retry·circuitbreaker |
| 테스트       | JUnit5, Spring Boot Test, H2                                   |                                        |

> 근거: context7로 Spring Boot 3.5.13(2026-04-12)·springdoc 2.8.17 호환 확인. RestClient는 spring-web 내장(동기), WebClient용 WebFlux 추가 불필요.

---

## 3. 아키텍처 — 모듈러 모놀리식 + 포트

### 3.1 도메인별 표준 레이어

각 도메인 하위 패키지는 **`controller / dto / domain / service / repository`** 기본 구조. 외부 경계가 있는 도메인에만 **`port`**(인터페이스 + 어댑터) 추가.

```
com.netzero.<domain>
├─ controller     REST 엔드포인트
├─ dto            요청/응답 (record)
├─ domain         엔티티 + 도메인 로직
├─ service        유스케이스 오케스트레이션
├─ repository     JPA 리포지토리
└─ port           (외부 경계 도메인) 인터페이스 + 어댑터 구현
```

### 3.2 도메인 목록과 포트

| 도메인       | port | 포트 내용                                                                                         |
| ------------ | ---- | ------------------------------------------------------------------------------------------------- |
| store        | —    | Store, **ItemMaster**, **OrderPolicy** 마스터                                                     |
| ingest       | —    | 판매/재고 CSV → SalesRecord/InventorySnapshot. 하루치 업로드는 `WeatherProvider`(KMA)로 날씨 보강 |
| **export**   | —    | sales/store-inventory CSV 월단위 추출 + **월말 S3 아카이빙·presigned URL**(AI에 판매CSV 전달)     |
| **weather**  | ✅   | `KmaForecastPort` + `KmaForecastClient`(@HttpExchange) — 기상청                                   |
| feature      | —    | `FeatureBuilder`(DB → 피처벡터), `HolidayCalendar`                                                |
| **forecast** | ✅   | `ForecastPort` + `AiForecastClient`(RestClient) — AI 서버                                         |
| order        | —    | `Newsvendor`, `QuantileInterpolator` (결정적)                                                     |
| carbon       | —    | `CarbonAccountingService` (결정적; EF는 ItemMaster)                                               |
| **chat**     | ✅   | `LlmPort` + `AiLlmClient`(RestClient) — AI 서버                                                   |
| pipeline     | —    | `DailyPipelineService`(오케스트레이터), `PipelineScheduler`                                       |
| dashboard    | —    | 집계                                                                                              |
| common       | —    | ApiResponse envelope, error(advice), metrics, config, BaseEntity                                  |

→ **포트는 weather·forecast·chat 3곳**(외부 호출 지점). 나머지는 표준 5레이어. 포트 인터페이스는 각각 테스트용 목 구현을 가져 AI 서버 없이 e2e 테스트 가능.

### 3.3 공유 DTO 인터페이스 — `WeatherSnapshot`

날씨는 weather 도메인·forecast 요청 양쪽에서 재사용하는 단일 정규화 DTO로 둔다.

```
WeatherSnapshot(forecastDate, avgTemp, precipitationMm, precipitationProb, skyCode)
```

- **기온은 평균온도(`avgTemp`) 단일 필드**로 전달(tempMax/tempMin 미사용). 엔티티에 max/min만 있으면 `avgTemp=(tempMax+tempMin)/2`로 파생.
- `KmaForecastPort` 응답 → `WeatherSnapshot` 정규화(단일 변환 지점).
- `weather.domain.WeatherForecast`(엔티티) ↔ `WeatherSnapshot`(DTO) 매핑.
- forecast 요청·DB 저장 모두 이 DTO를 경유.

---

## 4. 데이터 플로우

### 4.1 일일 파이프라인 (`@Scheduled` KST 새벽 / `POST /api/v1/pipeline/run`)

```
1. WeatherService.fetch()          → 기상청 익일 예보 → WeatherSnapshot → WeatherForecast 저장
2. FeatureBuilder.build()          → DB(SalesRecord+Weather)에서 카테고리별 피처벡터 조립
3. ForecastPort.predict(req)       → [AI서버] p10/p50/p90 수신 → DemandForecast 저장   (네트워크)
4. OrderOptimizationService.run()  → newsvendor Q*·실발주·ΔQ → OrderRecommendation 저장 (Spring 내부)
5. CarbonAccountingService.run()   → ΔE=ΣΔQ×(EF_prod+EF_waste), 보장/잠재 → CarbonSaving (Spring 내부)
```

- **멱등성:** 모든 결과 `(store_id, target_date)` upsert. 재실행 안전.
- **트랜잭션:** 3번(외부호출)은 트랜잭션 밖 + resilience4j. 4·5는 순수 인메모리 계산 후 저장.
- **동시성:** 동일 (store, date) 파이프라인 중복 실행 시 `PIPELINE_ALREADY_RUNNING`.

### 4.2 챗 플로우 (`POST /api/v1/chat`)

```
프론트 → Spring: { storeId, date, question }
  RagContextAssembler → DB에서 forecast+recommendation+carbon 근거 번들 조립
  LlmPort.generate({ question, grounding, locale:"ko" })  → [AI서버]
  AI → Spring: { answer, cacheHit, latencyMs, tokens }
Spring → 프론트: { answer, groundedOn:{ids}, cacheHit, llmLatencyMs, tokens }
```

- sLLM엔 **근거 수치를 주입만**, 수치 생성 금지(환각 차단).
- `cacheHit/tokens/latency`는 계측 메트릭으로 중계(저전력 입증).

---

## 5. AI 서버 계약 (AI 레포가 구현)

### 5.1 발주용 수요예측 — `POST {ai.base-url}/v1/order-recommendation`

발주주기·리드타임을 함께 전달해 **커버기간(리드타임+발주주기) 일별 예측**을 받는다. 발주주기 7일이면 7일치(+리드타임)를 합산해 발주량을 산출.

> 발주와 무관한 단순 익일 수요 표시는 별도 엔드포인트 `POST {ai.base-url}/v1/forecast`(단일일 분위)를 사용한다. 상세는 [`ai_server_api_spec.md`](../../ai_server_api_spec.md).

```json
Req: {
  "storeId": 1, "targetDate": "2026-06-27",
  "salesHistory": { "presignedUrls":[ "https://...s3.../sales-2026-05.csv?X-Amz-..." ], "format":"sales_csv_v1" },
  "coverage": { "leadTimeDays":1, "orderCycleDays":7, "coverageDays":8 },
  "weather": [ { "forecastDate":"2026-06-28","avgTemp":21.2,
                 "precipitationMm":12.0,"precipitationProb":80,"skyCode":4 } ],  // 커버기간 일수만큼(가용분), 기온=평균온도
  "rows": [{ "itemId":101, "orderCycleDays":7, "leadTimeDays":1,
    "features": { "dayOfWeek":6,"isHoliday":false,"ma7":9.4,"trend":-0.3 } }]
}
Res: { "modelVersion":"lgbm_global_v1",
  "predictions": [{ "itemId":101,
    "daily": [ {"date":"2026-06-28","p10":7.2,"p50":9.1,"p90":12.4}, ... ] }] }  // coverageDays개
```

- `coverage`/`weather`는 store·date 공통 top-level. 품목별 피처·발주정책은 `rows[]`.
- AI는 커버기간 **일별 분위**를 반환 → 백엔드가 합산(§7). 일별 미지원 모델은 단일 집계 분위로도 허용(Demo).
- LightGBM 미준비 시 AI가 `modelVersion:"baseline_v1"`로 ma7 기반 분위 반환(폴백 AI 책임).

### 5.2 자연어 — `POST {ai.base-url}/v1/generate`

```json
Req: { "question":"내일 우유 얼마나?", "locale":"ko",
  "grounding": { "forecast":{...}, "recommendation":{...}, "carbon":{...} } }
Res: { "answer":"...", "cacheHit":true, "latencyMs":180, "tokens":142 }
```

### 5.3 회복탄력성

- connect 3s / read 5s timeout, resilience4j retry 1회 + circuitbreaker.
- `/forecast` 실패 → 해당 파이프라인 `FORECAST_UNAVAILABLE`로 실패(Spring 폴백 없음).
- `/generate` 실패 → `/chat`에 `LLM_UNAVAILABLE`.

---

## 6. 도메인 모델 (요약)

상세 필드·제약은 [`backend_spec.md` §3](../../backend_spec.md) 참조. 핵심 엔티티:

| 엔티티                                      | 키                         | 비고                                                                                                                                            |
| ------------------------------------------- | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Store                                       | id                         | region·nx·ny(기상청 격자)                                                                                                                       |
| **ItemMaster** (구 Category+EmissionFactor) | id(품목ID)                 | 구분{완제품·원재료·판매음료·소모품}, 폐기대상, 관리단위, 유통기한, 보관조건, kgPerUnit, **efProd·efWaste**, 매입단가, 단가단위, 비고/출처(한글) |
| SalesRecord                                 | (store,item,date) UQ       | 날짜·요일·날씨{맑음·흐림·비}·기온·강수mm·행사·신메뉴·구분·품목·판매수량·비고\_시나리오                                                          |
| InventorySnapshot                           | (store,item,date) UQ       | onHandQuantity, **lastOrderDate(마지막발주일)**                                                                                                 |
| **OrderPolicy** (신규)                      | (store,item) UQ            | 발주방식·발주주기·리드타임·안전재고\_z·발주단위·비고                                                                                            |
| WeatherForecast                             | (region,date,fetchedAt) UQ | WeatherSnapshot 매핑                                                                                                                            |
| DemandForecast                              | (store,item,targetDate) UQ | p10/p50/p90, modelVersion, features(jsonb)                                                                                                      |
| OrderRecommendation                         | (store,item,targetDate) UQ | recommendedQty, optimalStockQty, criticalRatio, expectedWasteAvoidedKg, rationale(jsonb)                                                        |
| CarbonSaving                                | (store,item,targetDate) UQ | guaranteedSavingKg, potentialSavingKg, ef스냅샷                                                                                                 |

**Flyway:** `V1__schema.sql`, `V2__seed_item_master.sql`, `V3__seed_store_demo.sql`, `V4__seed_order_policy.sql`.
**InventorySnapshot 확장:** 일별 재고원장으로 확장 — 날짜·요일·품목·구분·단위·발주·기초재고·수요·실판매·결품·폐기·기말재고·폐기*kg·탄소\_kgCO2e·폐기*비용\_원·마지막발주일. (산출 컬럼은 적재 시 계산)
**데이터 추출(신규):** `GET /export/sales.csv`(SalesRecord 투영), `GET /export/store-inventory.csv`(InventorySnapshot 직접 투영) — 월 단위. 상세는 backend_spec.md §5.8.

---

## 7. 결정적 산정식 (Spring 내부)

- **커버기간 합산:** `H = leadTimeDays + orderCycleDays`. AI의 일별 분위를 합산 → `P10ₕ=Σp10, P50ₕ=Σp50, P90ₕ=Σp90` (예: 발주주기 7일 → 7일치 총합).
- **신문팔이:** `CR = Cu/(Cu+Co)`, `Q* = F⁻¹ₕ(CR)` (P10ₕ/P50ₕ/P90ₕ 구간선형보간), 실발주 `= roundToLot( max(0, Q*−현재고), orderLotUnit )`. 현재고=최신 기말재고.
  - CR≤0.10→P10ₕ 외삽 / 0.10–0.50→(P10ₕ,P50ₕ) / 0.50–0.90→(P50ₕ,P90ₕ) / >0.90→P90ₕ 외삽.
  - Co↑(폐기비용 큰 품목) → CR↓ → Q\*↓ 자동 보수화.
  - 분위 합산은 Demo 근사(comonotone). 단일 집계 분위 수신 시 그대로 사용.
- **폐기 회피:** `ΔQ = max(0, baseline − Q*)` → `×ItemMaster.kgPerUnit` (kg 환산). baseline=과거 동요일 평균×커버기간.
- 탄소: `ΔE = Σᵢ [ ΔQᵢ × (EFprod,ᵢ + EFwaste,ᵢ) ]`
  - ΔQᵢ = (기존 발주 기준 예상 폐기량) − (Copilot 추천 발주 기준 예상 폐기량), 카테고리 i 단위(kg)
  - EFprod = 전과정 생산 배출계수, EFwaste = 처리단계 배출계수 (kgCO₂eq/kg)
  - 보수적 공시 원칙상, 처리단계 회피분만 별도 분리해 ‘최소 보장 감축’으로, 생산 회피분 포함 시 ‘잠재 감축’으로 라벨을 구분해 제시하면 과대계상 논란을 피할 수 있다.
- **정확도:** `WAPE = Σ|actual−pred| / Σactual` (품목별).

---

## 8. REST API (요약)

베이스 `/api/v1` · JSON · KST · 응답 envelope `{success,data}`. 상세 요청/응답 JSON은 [`backend_api_spec.md`](../../backend_api_spec.md).

| 그룹     | 엔드포인트                                                                                          |
| -------- | --------------------------------------------------------------------------------------------------- |
| 수집     | `POST /ingest/sales`, `POST /ingest/sales/daily`, `POST /ingest/inventory`, `POST /weather/refresh` |
| 분석     | `POST /pipeline/run`, `GET /forecast`, `GET /recommendations`, `GET /recommendations/{id}`          |
| 탄소     | `GET /carbon/today`, `GET /carbon/savings`, `GET /carbon/savings/summary`                           |
| 대시보드 | `GET /dashboard/summary`                                                                            |
| 챗       | `POST /chat`                                                                                        |
| 추출     | `GET /export/sales.csv`, `GET /export/store-inventory.csv`, `POST /export/archive`                  |
| 운영     | `/actuator/*`, `/swagger-ui.html`, `/v3/api-docs`                                                   |

---

## 9. 에러 처리 & 테스트 전략

### 9.1 에러

`ApiResponse<T>` envelope(`{success,data}` / `{success,error{code,message}}`) + `@RestControllerAdvice`. code 카탈로그:
`VALIDATION_ERROR`, `INVALID_CSV`, `ITEM_NOT_FOUND`, `STORE_NOT_FOUND`, `CONTENT_NOT_FOUND`, `FORECAST_UNAVAILABLE`, `WEATHER_FETCH_FAILED`, `LLM_UNAVAILABLE`, `PIPELINE_ALREADY_RUNNING`, `INTERNAL_ERROR`. 상세는 `backend_api_spec.md` §1.

### 9.2 테스트 (TDD — 결정적 코어 우선)

- **단위:** `QuantileInterpolator`(보간 경계값), `Newsvendor`(CR→Q\* 단조성), `CarbonAccountingService`(산정식·보장/잠재 분리). 결정적이라 100% 단위테스트 대상.
- **슬라이스:** `@WebMvcTest`(컨트롤러), `@DataJpaTest`(리포지토리, H2).
- **통합:** `ForecastPort`/`LlmPort` 목 구현으로 파이프라인 e2e(AI 서버 없이 그린).
- **계약:** AI 서버 응답 JSON 픽스처로 `AiForecastClient`/`AiLlmClient` 역직렬화 검증.

### 9.3 계측 (Micrometer)

`zerowave.llm.calls`, `zerowave.llm.tokens`, `zerowave.llm.latency`, `zerowave.llm.cache.hit/miss`, `zerowave.pipeline.duration`, `zerowave.forecast.wape`(카테고리 태그). `/actuator/metrics`.

---

## 10. 구현 마일스톤

1. **M0 — 빌드 갱신:** Spring Boot 3.5.13, springdoc 2.8.17, RestClient, Flyway, actuator, resilience4j. 부팅 확인.
2. **M1 — 골격:** Flyway 스키마+시드(ItemMaster/데모매장/OrderPolicy), `/ingest/sales`·`/ingest/sales/daily`·`/ingest/inventory`, 합성데이터 적재, CSV 추출.
3. **M2 — 결정적 코어(TDD):** newsvendor 발주 → 탄소 산정 → `/recommendations`, `/carbon/today`. (AI 서버 목으로 e2e)
4. **M3 — 외부연동:** 기상청 수집(@HttpExchange), FeatureBuilder, `ForecastPort`(AI 실연동), `/pipeline/run`, `/dashboard/summary`.
5. **M4 — 챗:** `LlmPort` 실연동, `RagContextAssembler`, `/chat` + 계측.
6. **M5 — 시연:** WAPE 로깅, 데모 시나리오, AI 레포 통합 테스트.

---

## 11. 향후 이행 (idea_note 목표 아키텍처)

포트 추상화로 구현체만 교체:

| 항목       | 해커톤              | 목표                               |
| ---------- | ------------------- | ---------------------------------- |
| 저장소     | AWS RDS (MySQL 8)   | DynamoDB (Repository 교체)         |
| 배치       | `@Scheduled`        | Lambda+EventBridge (scale-to-zero) |
| AI 서버    | FastAPI 사이드카    | EC2+Inferentia(Inf2)               |
| 계측       | Micrometer/Actuator | CloudWatch                         |
| 프레임워크 | Spring Boot 3.5.13  | (장기) 4.0.x + springdoc 3.0.x     |

---

## 부록 — 미해결/AI 레포 합의 필요

- AI 서버 `/v1/order-recommendation`(발주용 커버기간)·`/v1/forecast`(익일 단일)·`/v1/generate` 계약(§5, ai_server_api_spec.md)을 AI 레포와 확정.
- `grounding` 번들 스키마 상세(§4.2) — sLLM 프롬프트 설계와 함께 합의.
- `baseline_v1` 폴백 산식(ma7 기반)을 AI 레포가 구현.
