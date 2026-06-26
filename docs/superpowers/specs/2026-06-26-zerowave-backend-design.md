# Zero-Waste Copilot — Spring 백엔드 설계 (브레인스토밍 산출 스펙)

> 팀 **Zero:Wave** · 작성일 2026-06-26 · 검증기준일 2026-06-26
> 제품 배경: [`../../idea_note.md`](../../idea_note.md) · 기술 상세 스펙: [`../../backend_spec.md`](../../backend_spec.md)
> 본 문서는 brainstorming 단계의 **검증된 설계(spec)** 이다. 다음 단계는 writing-plans(구현 계획).

---

## 1. 목표와 범위

POS·날씨·요일 데이터를 결합해 **익일 카테고리별 최적 발주량**을 산출하고, 과발주로 발생했을 폐기량을 **공인 배출계수 기반 절감 탄소(kgCO₂eq)** 로 환산해 점주에게 제공한다. 모든 정량 수치는 결정적 로직으로 산출하고, 자연어 설명만 sLLM에 위임한다(수치 환각 차단·추론 전력 최소화 — idea_note §3).

### 1.1 시스템 경계 (이번 브레인스토밍에서 확정)
| 계층 | 책임 | 기술 | 소유 |
|------|------|------|------|
| ① 프론트엔드 | 대시보드·챗봇 UI | React/Vue + Amplify | 별도 레포 |
| **② Spring 백엔드(본 레포)** | ETL · 피처조립 · **발주최적화** · **탄소산정** · 영속화 · API · 오케스트레이션 · AI 서버 호출 | Spring Boot 3.5.13 / Java 21 | **본 레포** |
| ③ **Python AI 서버** | **LightGBM 수요예측 추론** + **sLLM 자연어**(예측 폴백/베이스라인 포함) | Python FastAPI | **별도 레포** |

**결정 사항:**
1. **경계:** ML 추론(LightGBM)과 sLLM만 AI 서버로 분리. 신문팔이 발주최적화·탄소산정은 **Spring 내부 결정적 로직**으로 유지.
2. **연동:** AI 서버는 **상태 없는 추론 API**. Spring이 DB에서 피처·날씨를 조립해 전송, AI는 분위예측/자연어만 반환(DB 접근 없음).
3. **챗 경로:** 프론트 → Spring → AI 서버. Spring이 RAG 근거(예측·발주·탄소)를 DB에서 조립해 주입.
4. **예측 폴백:** LightGBM 미준비/실패 시 **AI 서버**가 단순 베이스라인 반환. Spring은 자체 예측 폴백을 보유하지 않는다(순수 결정적 유지).
5. **아키텍처:** 모듈러 모놀리식 + 얇은 포트(외부 경계만 인터페이스화).

### 1.2 비목표 (YAGNI)
- Spring 내 ML 학습·추론, 자연어 생성, 자체 예측 폴백 로직.
- 이벤트소싱/아웃박스/CQRS(하루 1회 배치엔 과함).
- UI 렌더링.

---

## 2. 기술 스택 (최신 검증값, 2026-06-26)

| 구분 | 선택 | 비고 |
|------|------|------|
| 런타임 | Java 21 (LTS) | 현 Gradle toolchain |
| 프레임워크 | **Spring Boot 3.5.13** | 현 build.gradle 3.5.3 → 패치 업 |
| Web | spring-boot-starter-web (동기 MVC) | WebFlux 미채택 |
| 영속성 | spring-data-jpa + **PostgreSQL 17** (H2 테스트) | |
| 마이그레이션 | Flyway | 스키마/시드 |
| 외부 HTTP | **RestClient**(AI 서버) + **`@HttpExchange`**(기상청) | webflux 의존성 없음 |
| 검증 | spring-boot-starter-validation | |
| 문서 | **springdoc-openapi-starter-webmvc-ui 2.8.17** | `/swagger-ui.html` |
| 관측 | actuator + Micrometer | 저전력 계측 |
| 회복탄력성 | resilience4j 2.2.0 | AI/기상청 timeout·retry·circuitbreaker |
| 테스트 | JUnit5, Spring Boot Test, H2 | |

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
| 도메인 | port | 포트 내용 |
|---|---|---|
| store | — | Store, Category 마스터 |
| ingest | — | POS/재고 CSV → SalesRecord/InventorySnapshot |
| **weather** | ✅ | `KmaForecastPort` + `KmaForecastClient`(@HttpExchange) — 기상청 |
| feature | — | `FeatureBuilder`(DB → 피처벡터), `HolidayCalendar` |
| **forecast** | ✅ | `ForecastPort` + `AiForecastClient`(RestClient) — AI 서버 |
| order | — | `Newsvendor`, `QuantileInterpolator` (결정적) |
| carbon | — | `CarbonAccountingService`, EmissionFactor (결정적) |
| **chat** | ✅ | `LlmPort` + `AiLlmClient`(RestClient) — AI 서버 |
| pipeline | — | `DailyPipelineService`(오케스트레이터), `PipelineScheduler` |
| dashboard | — | 집계 |
| common | — | error(ProblemDetail advice), metrics, config, BaseEntity |

→ **포트는 weather·forecast·chat 3곳**(외부 호출 지점). 나머지는 표준 5레이어. 포트 인터페이스는 각각 테스트용 목 구현을 가져 AI 서버 없이 e2e 테스트 가능.

### 3.3 공유 DTO 인터페이스 — `WeatherSnapshot`
날씨는 weather 도메인·forecast 요청 양쪽에서 재사용하는 단일 정규화 DTO로 둔다.
```
WeatherSnapshot(forecastDate, tempMax, tempMin, precipitationMm, precipitationProb, skyCode)
```
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

### 5.1 수요예측 — `POST {ai.base-url}/v1/forecast`
```json
Req: {
  "storeId": 1, "targetDate": "2026-06-27",
  "weather": { "forecastDate":"2026-06-27","tempMax":24.1,"tempMin":18.3,
               "precipitationMm":12.0,"precipitationProb":80,"skyCode":4 },
  "rows": [{ "categoryCode":"MILK",
    "features": { "dayOfWeek":6,"isHoliday":false,"ma7":9.4,"trend":-0.3 } }]
}
Res: { "modelVersion":"lgbm_global_v1",
  "predictions": [{ "categoryCode":"MILK","p10":7.2,"p50":9.1,"p90":12.4 }] }
```
- 날씨는 store/date 공통이라 **top-level `weather` 블록**으로 분리. 카테고리별 피처는 `rows[].features`.
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

| 엔티티 | 키 | 비고 |
|---|---|---|
| Store | id | region·nx·ny(기상청 격자) |
| Category | code(UQ) | shelfLifeDays, unit, densityKgPerUnit |
| SalesRecord | (store,category,date,source) UQ | quantitySold, wasteQuantity |
| InventorySnapshot | (store,category,date) UQ | onHandQuantity |
| WeatherForecast | (region,date,fetchedAt) UQ | WeatherSnapshot 매핑 |
| DemandForecast | (store,category,targetDate) UQ | p10/p50/p90, modelVersion, features(jsonb) |
| OrderRecommendation | (store,category,targetDate) UQ | recommendedQty, optimalStockQty, criticalRatio, expectedWasteAvoidedKg, rationale(jsonb) |
| CarbonSaving | (store,category,targetDate) UQ | guaranteedSavingKg, potentialSavingKg |
| EmissionFactor | (categoryCode,validFrom) | efProdKg, efWasteKg, sourceRef |

**Flyway:** `V1__schema.sql`, `V2__seed_category.sql`, `V3__seed_emission_factor.sql`, `V4__seed_store_demo.sql`.

---

## 7. 결정적 산정식 (Spring 내부)

- **신문팔이:** `CR = Cu/(Cu+Co)`, `Q* = F⁻¹(CR)` (p10/p50/p90 구간선형보간), 실발주 `= max(0, Q*−재고)`.
  - CR≤0.10→p10 외삽 / 0.10–0.50→(p10,p50) / 0.50–0.90→(p50,p90) / >0.90→p90 외삽.
  - Co↑(폐기비용 큰 품목) → CR↓ → Q*↓ 자동 보수화.
- **폐기 회피:** `ΔQ = max(0, baseline − Q*)` → `×densityKgPerUnit` (kg 환산). baseline=과거 동요일 평균.
- **탄소:** `ΔE = Σ ΔQᵢ × (EF_prod,ᵢ + EF_waste,ᵢ)`
  - 보장 = `Σ ΔQᵢ × EF_waste,ᵢ` / 잠재 = 전체.
- **정확도:** `WAPE = Σ|actual−pred| / Σactual` (카테고리별).

---

## 8. REST API (요약)

베이스 `/api/v1` · JSON · KST. 상세 요청/응답은 [`backend_spec.md` §5](../../backend_spec.md).

| 그룹 | 엔드포인트 |
|---|---|
| 수집 | `POST /ingest/pos`, `POST /ingest/inventory`, `POST /weather/refresh` |
| 분석 | `POST /pipeline/run`, `GET /forecast`, `GET /recommendations`, `GET /recommendations/{id}` |
| 탄소 | `GET /carbon/today`, `GET /carbon/savings`, `GET /carbon/savings/summary` |
| 대시보드 | `GET /dashboard/summary` |
| 챗 | `POST /chat`, `GET /chat/context` |
| 운영 | `/actuator/*`, `/swagger-ui.html`, `/v3/api-docs` |

---

## 9. 에러 처리 & 테스트 전략

### 9.1 에러
`@RestControllerAdvice` + `ProblemDetail`(RFC 7807) + code 카탈로그:
`INVALID_CSV`, `INVALID_CATEGORY`, `STORE_NOT_FOUND`, `FORECAST_UNAVAILABLE`, `LLM_UNAVAILABLE`, `WEATHER_FETCH_FAILED`, `PIPELINE_ALREADY_RUNNING`, `VALIDATION_ERROR`.

### 9.2 테스트 (TDD — 결정적 코어 우선)
- **단위:** `QuantileInterpolator`(보간 경계값), `Newsvendor`(CR→Q* 단조성), `CarbonAccountingService`(산정식·보장/잠재 분리). 결정적이라 100% 단위테스트 대상.
- **슬라이스:** `@WebMvcTest`(컨트롤러), `@DataJpaTest`(리포지토리, H2).
- **통합:** `ForecastPort`/`LlmPort` 목 구현으로 파이프라인 e2e(AI 서버 없이 그린).
- **계약:** AI 서버 응답 JSON 픽스처로 `AiForecastClient`/`AiLlmClient` 역직렬화 검증.

### 9.3 계측 (Micrometer)
`zerowave.llm.calls`, `zerowave.llm.tokens`, `zerowave.llm.latency`, `zerowave.llm.cache.hit/miss`, `zerowave.pipeline.duration`, `zerowave.forecast.wape`(카테고리 태그). `/actuator/metrics`.

---

## 10. 구현 마일스톤

1. **M0 — 빌드 갱신:** Spring Boot 3.5.13, springdoc 2.8.17, RestClient, Flyway, actuator, resilience4j. 부팅 확인.
2. **M1 — 골격:** Flyway 스키마+시드(카테고리/배출계수/데모매장), `/ingest/pos`, 합성데이터 적재.
3. **M2 — 결정적 코어(TDD):** newsvendor 발주 → 탄소 산정 → `/recommendations`, `/carbon/today`. (AI 서버 목으로 e2e)
4. **M3 — 외부연동:** 기상청 수집(@HttpExchange), FeatureBuilder, `ForecastPort`(AI 실연동), `/pipeline/run`, `/dashboard/summary`.
5. **M4 — 챗:** `LlmPort` 실연동, `RagContextAssembler`, `/chat` + 계측.
6. **M5 — 시연:** WAPE 로깅, 데모 시나리오, AI 레포 통합 테스트.

---

## 11. 향후 이행 (idea_note 목표 아키텍처)
포트 추상화로 구현체만 교체:
| 항목 | 해커톤 | 목표 |
|---|---|---|
| 저장소 | PostgreSQL 17 | DynamoDB (Repository 교체) |
| 배치 | `@Scheduled` | Lambda+EventBridge (scale-to-zero) |
| AI 서버 | FastAPI 사이드카 | EC2+Inferentia(Inf2) |
| 계측 | Micrometer/Actuator | CloudWatch |
| 프레임워크 | Spring Boot 3.5.13 | (장기) 4.0.x + springdoc 3.0.x |

---

## 부록 — 미해결/AI 레포 합의 필요
- AI 서버 `/v1/forecast`·`/v1/generate` 계약(§5)을 AI 레포와 확정.
- `grounding` 번들 스키마 상세(§4.2) — sLLM 프롬프트 설계와 함께 합의.
- `baseline_v1` 폴백 산식(ma7 기반)을 AI 레포가 구현.
