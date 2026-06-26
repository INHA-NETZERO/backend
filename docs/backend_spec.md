# Zero-Waste Copilot — 백엔드 스펙 (v2)

> 팀 **Zero:Wave** / F&B 식자재 폐기물 제로화를 위한 발주 최적화·탄소 산정 백엔드
> 본 문서는 Spring Boot 백엔드 레포의 구현 스펙이다. 제품 배경은 [`idea_note.md`](./idea_note.md) 참고.
> **검증 기준일: 2026-06-26** (라이브러리 최신 버전은 context7 문서로 교차검증함 — §2.3 버전 감사 표 참고).

---

## 1. 개요 및 책임 경계

### 1.1 한 줄 정의
POS·날씨·요일 데이터를 결합해 **익일 카테고리별 최적 발주량**을 산출하고, 과발주로 발생했을 폐기량을 **공인 배출계수 기반 절감 탄소(kgCO₂eq)** 로 환산해 점주에게 제공하는 결정적(deterministic) 분석 백엔드.

### 1.2 시스템 구성과 본 레포의 위치
전체 시스템은 3개 계층으로 나뉜다. 본 레포는 **②결정적 분석/오케스트레이션 계층**을 담당한다.

| 계층 | 역할 | 기술 | 소유 |
|------|------|------|------|
| ① 프론트엔드 | 대시보드·챗봇 UI | React/Vue + Amplify | 별도 레포 |
| **② 백엔드(본 레포)** | **ETL · 수요예측 연동 · 발주 최적화 · 탄소 산정 · API 게이트웨이** | **Spring Boot 3.5.x / Java 21** | **본 레포** |
| ③ 언어 계층(sLLM) | 자연어 설명·대화(RAG·시맨틱 캐싱) | Python FastAPI + HF Transformers | 별도 레포(또는 사이드카) |

> **설계 원칙(idea_note §3 기술):** 모든 정량 수치(예측·발주·탄소)는 본 백엔드의 결정적 로직에서 산출한다. sLLM은 수치를 **생성하지 않고** 본 백엔드가 만든 값을 문장으로 설명만 한다 → LLM 수치 환각 원천 차단, 추론 전력 최소화.

### 1.3 백엔드가 하는 일 / 하지 않는 일
- **한다:** POS CSV 수집·정제, 날씨 수집, 피처 생성, 수요예측(모델 호출/추론), 신문팔이 발주 최적화, 탄소 절감 산정, 결과 영속화, REST API 제공, sLLM 컨텍스트(RAG 근거) 제공, 저전력 계측 메트릭 노출.
- **하지 않는다:** 자연어 생성(③ 위임), UI 렌더링(① 위임), ML 모델 학습(오프라인 배치/노트북). 백엔드는 학습된 산출물의 **추론·소비**만 담당한다.

---

## 2. 기술 스택 (최신 버전 검증 완료)

### 2.1 확정 스택
| 구분 | 선택 | 비고 |
|------|------|------|
| 언어/런타임 | **Java 21 (LTS)** | 현 Gradle toolchain. Java 25 LTS(2025-09)도 가용하나 21 유지 권장(생태계 안정) |
| 프레임워크 | **Spring Boot 3.5.x** (목표 패치 **3.5.13+**) | 현 `build.gradle`은 3.5.3 → **3.5.13로 패치 업** 필요(§2.3) |
| 빌드 | Gradle 8.14.5 (wrapper) | 현행 유지 |
| Web | spring-boot-starter-web (Servlet/Tomcat, 동기) | 본 서비스는 동기 워크로드. **WebFlux 불채택**(§2.4) |
| 영속성 | spring-boot-starter-data-jpa + **PostgreSQL 17** | H2는 테스트 전용 |
| 마이그레이션 | **Flyway** | 스키마/시드 버전관리 |
| HTTP 외부호출 | **RestClient**(spring-web 내장) + **선언형 HTTP Interface(`@HttpExchange`)** | 기상청·sLLM 호출. **webflux 의존성 추가 안 함**(§2.4) |
| 검증 | spring-boot-starter-validation (Jakarta Bean Validation) | |
| API 문서 | **springdoc-openapi-starter-webmvc-ui 2.8.x** (최신 2.8.17) | `/swagger-ui.html` |
| 관측/계측 | spring-boot-starter-actuator + Micrometer | 저전력 입증 메트릭(§8) |
| 회복탄력성 | resilience4j (timeout/retry/circuitbreaker) | 외부 API·sLLM 폴백 |
| 테스트 | JUnit5, Spring Boot Test, H2, (선택) Testcontainers-postgres | |

### 2.2 `build.gradle` 변경안 (diff)
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.13'          // 3.5.3 → 3.5.13 (최신 패치)
    id 'io.spring.dependency-management' version '1.1.7'
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'   // 추가
    implementation 'org.springframework.boot:spring-boot-starter-actuator'     // 추가
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.flywaydb:flyway-core'                                  // 추가
    runtimeOnly  'org.postgresql:postgresql'                                   // 추가
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17'  // 추가
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'    // 추가(외부호출 회복)
    // 주의: spring-boot-starter-webflux 는 추가하지 않는다 → RestClient 로 충분(§2.4)

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

### 2.3 버전 감사 (현행 → 권장, 검증 출처: context7)
| 라이브러리 | 현행/초안 | 최신 확인값(2026-06) | 조치 |
|---|---|---|---|
| Spring Boot | 3.5.3 | 3.5.13 (3.5.x 최신 패치) · 4.0.x 존재 | **3.5.13로 패치 업** (4.0은 §11 이행 옵션) |
| springdoc-openapi | 초안 2.6.0 | **2.8.17** (Spring Boot 3.5.13 호환) | 2.8.17 채택 |
| HTTP client | 초안 WebFlux+WebClient | RestClient / `@HttpExchange` | **WebFlux 제거**, RestClient 채택 |
| PostgreSQL | 초안 16 | 17 | 17 채택(드라이버는 starter가 관리) |
| Java | 21 | 21 LTS(25 LTS도 가용) | 21 유지 |

> Spring Boot 4.0.x로 갈 경우 springdoc는 **3.0.3+** 스타터를 써야 한다. 해커톤 기간엔 3.5.13 라인 권장(브레이킹 변경 회피).

### 2.4 HTTP 클라이언트 결정 — 왜 WebClient/WebFlux가 아닌가 (이전 초안의 outdated 부분)
- 이전 초안은 외부 호출용으로 `spring-boot-starter-webflux`를 추가해 `WebClient`를 쓰자고 했으나, **본 서비스는 동기 MVC 워크로드**이고 리액티브 런타임이 불필요하다.
- Spring Boot 3.2+에는 동기 fluent 클라이언트 **`RestClient`**(spring-web 내장)가 있어 **추가 의존성 없이** 기상청·sLLM 호출이 가능하다.
- 다수 엔드포인트를 가진 외부 API(기상청)는 **선언형 HTTP Interface(`@HttpExchange`)** + `HttpServiceProxyFactory`로 타입 안전하게 래핑한다.
- 타임아웃/재시도/서킷브레이커는 resilience4j로 처리(RestClient 자체엔 내장 retry 없음).

```java
// config/HttpClientConfig.java
@Configuration
class HttpClientConfig {
  @Bean
  RestClient kmaRestClient(RestClient.Builder builder,
                           @Value("${external.kma.base-url}") String baseUrl) {
    var settings = ClientHttpRequestFactorySettings.defaults()
        .withConnectTimeout(Duration.ofSeconds(3))
        .withReadTimeout(Duration.ofSeconds(5));
    return builder.baseUrl(baseUrl)
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }
  @Bean
  KmaForecastClient kmaForecastClient(RestClient kmaRestClient) {
    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(kmaRestClient))
        .build().createClient(KmaForecastClient.class);
  }
}

// weather/KmaForecastClient.java — 선언형 HTTP Interface
interface KmaForecastClient {
  @GetExchange("/getVilageFcst")
  KmaResponse getVillageForecast(@RequestParam Map<String,String> query);
}
```

---

## 3. 도메인 모델

### 3.1 핵심 엔티티 관계
```
Store 1───* SalesRecord *───1 Category
        1───* InventorySnapshot
        1───* DemandForecast      *───1 Category
        1───* OrderRecommendation *───1 Category
        1───* CarbonSaving        *───1 Category
WeatherForecast (region·date 단위)
EmissionFactor (category 단위, 정적 룩업, 버전관리)
```

### 3.2 엔티티 정의 (필드·타입·제약)

#### Store — 매장
| 필드 | 타입 | 제약/설명 |
|------|------|------|
| id | Long PK | identity |
| name | varchar(100) | NOT NULL |
| region | varchar(40) | 기상청 지역코드 매핑 |
| nx, ny | int | 기상청 격자 좌표 |
| createdAt | timestamptz | default now() |

#### Category — 식자재 카테고리
| 필드 | 타입 | 제약/설명 |
|------|------|------|
| id | Long PK | |
| code | varchar(30) | **UNIQUE** (예: `MILK`,`BAKERY`,`VEG`,`BEEF`) |
| name | varchar(60) | |
| shelfLifeDays | int | 유통기한(신문팔이 단일기간 판단) |
| unit | varchar(8) | `kg`/`L`/`ea` |
| densityKgPerUnit | numeric(10,4) | L/ea → kg 환산계수(탄소 산정용) |

#### SalesRecord — POS 일별 카테고리 매출(정제 후)
| 필드 | 타입 | 제약/설명 |
|------|------|------|
| id | Long PK | |
| store_id, category_id | FK | NOT NULL |
| businessDate | date | NOT NULL |
| quantitySold | numeric(12,3) | ≥0 |
| revenue | numeric(14,2) | nullable |
| wasteQuantity | numeric(12,3) | 실제 폐기량(있으면) |
| source | varchar(12) | `POS_CSV`/`SYNTHETIC` |
> **UNIQUE(store_id, category_id, businessDate, source)** — 재적재 멱등성.

#### InventorySnapshot — 일별 재고
| store_id, category_id, businessDate, onHandQuantity numeric(12,3) | 발주 기준 재고. UNIQUE(store,category,date) |

#### WeatherForecast — 기상청 예보(피처 원천)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| region | varchar(40) | |
| forecastDate | date | 예보 대상일(=익일) |
| tempMax, tempMin | numeric(4,1) | ℃ |
| precipitationMm | numeric(6,2) | 강수량 |
| precipitationProb | int | 강수확률 % (0~100) |
| skyCode | int | 하늘상태(1맑음~4흐림) |
| fetchedAt | timestamptz | 수집 시각 |
> UNIQUE(region, forecastDate, fetchedAt) — 동일일 다회 수집 이력 보존, 조회는 최신 fetchedAt.

#### DemandForecast — 수요예측 결과
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, category_id | FK | |
| targetDate | date | 예측 대상일(익일) |
| predictedQuantity | numeric(12,3) | 점추정(p50) |
| p10, p50, p90 | numeric(12,3) | 분위 예측(신문팔이 입력) |
| modelVersion | varchar(40) | 예: `lgbm_global_v1`, `stats_fallback_v1` |
| features | jsonb | 사용 피처 스냅샷(재현성·RAG) |
> UNIQUE(store, category, targetDate) — upsert.

#### OrderRecommendation — 발주 추천
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, category_id | FK | |
| targetDate | date | |
| recommendedQuantity | numeric(12,3) | 신문팔이 최적 발주량 후 재고차감 실발주 |
| optimalStockQuantity | numeric(12,3) | Q* (재고차감 전 목표재고) |
| baselineQuantity | numeric(12,3) | 비교 기준(관행 발주) |
| criticalRatio | numeric(6,4) | Cu/(Cu+Co) |
| expectedWasteAvoidedKg | numeric(12,3) | baseline 대비 줄인 예상 폐기량(kg) |
| rationale | jsonb | 산출 근거(피처·분위·비용) → RAG 근거 |
> UNIQUE(store, category, targetDate).

#### CarbonSaving — 탄소 절감 산정
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, category_id | FK | |
| targetDate | date | |
| wasteAvoidedKg | numeric(12,3) | ΔQ(kg) |
| guaranteedSavingKg | numeric(12,3) | 최소보장(처리 회피, EF_waste) |
| potentialSavingKg | numeric(12,3) | 잠재(생산+처리, EF_prod+EF_waste) |
| emission_factor_id | FK | 사용 계수 버전 |
> UNIQUE(store, category, targetDate).

#### EmissionFactor — 배출계수 정적 룩업
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| categoryCode | varchar(30) | |
| efProdKg | numeric(8,3) | 생산 전과정 kgCO₂eq/kg (Poore & Nemecek 2018) |
| efWasteKg | numeric(8,3) | 폐기 처리 kgCO₂eq/kg |
| sourceRef | varchar(60) | 출처(`OWID/Poore2018`) |
| validFrom | date | 버전관리(조회는 ≤targetDate 중 최신) |
> 시드 예시: 소고기 efProd≈60, 치즈≈21, 닭≈6, 돼지≈7, 우유≈3.

### 3.3 Flyway 마이그레이션 구성
```
src/main/resources/db/migration/
  V1__schema.sql            -- 위 테이블 DDL + 인덱스/제약
  V2__seed_category.sql     -- 카테고리(코드·유통기한·단위·밀도)
  V3__seed_emission_factor.sql  -- 배출계수 룩업
  V4__seed_store_demo.sql   -- 데모 매장(기상청 격자 포함)
```
인덱스 권장: 모든 결과 테이블에 `(store_id, target_date)` / SalesRecord에 `(store_id, business_date)`.

---

## 4. 결정적 분석 모듈 (백엔드 핵심)

### 4.1 ETL / 피처 파이프라인
```
POS CSV ─┐
         ├─► 정제(결측·이상치·단위표준화) ─► SalesRecord
재고 CSV ─┘
기상청 API ─► WeatherForecast
                    │
                    ▼
   FeatureBuilder → 피처벡터:
   [dayOfWeek, isHoliday, precipitationMm, precipitationProb,
    tempMax, tempMin, ma7(최근7일 이동평균), trend, storeId, categoryCode]
```
- 공휴일은 정적 테이블(또는 공휴일 API)로 판정.
- 피처는 `DemandForecast.features(jsonb)`에 스냅샷 저장(재현성·RAG 근거).

### 4.2 수요예측 (DemandForecastService)
- **모델:** 전 매장 통합 글로벌 모델 1개(LightGBM). 매장ID·카테고리를 피처로 사용.
- **연동 방식(택1, 해커톤 기본 A):**
  - **A. Python 추론 사이드카** — 학습된 LightGBM을 Python(FastAPI)이 서빙, Spring이 **RestClient**로 피처 전달→분위 예측 수신. (역할 분리 명확, 권장)
  - B. ONNX 변환 후 JVM 인-프로세스 추론(`onnxruntime`).
  - **C. (폴백) 자바 통계 baseline** — 요일×카테고리 ma7 + 날씨 보정. 모델 미가용 시 graceful degradation, `modelVersion=stats_fallback_v1`.
- **출력:** `predictedQuantity(p50)`, 분위 `p10/p50/p90`.
- **정확도 관리:** WAPE = Σ|actual−pred| / Σactual 을 카테고리별 산출·로깅. 신선식품은 목표 차등.

**예측 서비스 계약(A 방식, sidecar):**
```
POST {forecast.sidecar.base-url}/predict
Req:  { "rows":[{ "storeId":1,"categoryCode":"MILK","features":{...} }, ...] }
Res:  { "predictions":[{ "categoryCode":"MILK","p10":7.2,"p50":9.1,"p90":12.4,"modelVersion":"lgbm_global_v1" }] }
```

### 4.3 발주 최적화 (OrderOptimizationService) — 신문팔이(Newsvendor) 모형
유통기한 짧은 단일기간 품목에 표준 안전재고 대신 newsvendor 적용.

```
임계분위(Critical Ratio):  CR = Cu / (Cu + Co)
  Cu = 과소(결품) 단위비용 = 판매단가 − 원가  (기회손실/마진)
  Co = 과잉(폐기) 단위비용 = 원가 + 폐기처리비 (+ 탄소페널티 옵션)
최적 목표재고:  Q* = F⁻¹(CR)              # 수요분포의 CR 분위수
실 발주량:      order = max(0, Q* − onHandQuantity)
```
- **분위 보간(F⁻¹ 근사):** p10/p50/p90 세 점을 **구간선형보간(piecewise-linear)**.
  - CR ≤ 0.10 → p10 이하 외삽, 0.10<CR≤0.50 → (p10,p50) 선형, 0.50<CR≤0.90 → (p50,p90) 선형, CR>0.90 → p90 이상 외삽.
- **자동 보수화:** 폐기비용 Co가 큰 품목일수록 CR↓ → Q*↓ → 발주 보수화(idea_note 핵심).
- **ΔQ(예상 폐기 회피량):** `expectedWasteAvoided = max(0, baselineQuantity − Q*)` 를 kg 환산(`densityKgPerUnit`).
  - `baselineQuantity`는 관행 발주 추정(과거 동요일 평균 또는 p90 발주 같은 점주 관성치) — 시연 기본은 **과거 동요일 평균**.

```java
double criticalRatio = cu / (cu + co);
double qStar = quantileInterpolate(p10, p50, p90, criticalRatio); // 위 보간규칙
double order = Math.max(0, qStar - onHand);
double wasteAvoidedQty = Math.max(0, baseline - qStar);
double wasteAvoidedKg  = wasteAvoidedQty * category.densityKgPerUnit();
```

### 4.4 탄소 산정 (CarbonAccountingService)
산정식(idea_note §3):
```
ΔE = Σᵢ ΔQᵢ(kg) × (EF_prod,ᵢ + EF_waste,ᵢ)
```
- 룩업은 `EmissionFactor`에서 O(1) 조회(categoryCode, validFrom≤targetDate 최신).
- **두 라벨 분리 보고:**
  - `guaranteedSavingKg = Σ ΔQᵢ × EF_waste,ᵢ` — 최소 보장(폐기 처리 회피, 확실).
  - `potentialSavingKg  = Σ ΔQᵢ × (EF_prod,ᵢ + EF_waste,ᵢ)` — 잠재(상류 생산 회피 포함).
- 환산: 판매단위(L/ea)→kg는 `Category.densityKgPerUnit`로 표준화.
- 대시보드 보조: 승용차 환산 ≈ `potentialSavingKg / 4.6kg(승용차 km당 평균)` 등 표시지표(상수는 config).

### 4.5 일일 배치 오케스트레이션 (DailyPipelineService)
하루 1회 실행(idea_note: Lambda+EventBridge → 해커톤은 `@Scheduled` + 수동 트리거):
```
1) 기상청 익일 예보 수집  → WeatherForecast
2) 피처 생성              → FeatureBuilder
3) 카테고리별 수요예측    → DemandForecast (A: sidecar / C: 폴백)
4) 신문팔이 발주 최적화   → OrderRecommendation
5) 탄소 절감 산정         → CarbonSaving
6) 결과 upsert + 산정 로그(메트릭)
```
- **멱등성:** `(store_id, target_date)` upsert. 재실행 안전.
- **트랜잭션:** 단계별 `@Transactional`, 외부호출(1·3A)은 트랜잭션 밖 + resilience4j.
- **트리거:** `POST /pipeline/run`(시연 수동) + `@Scheduled(cron)` (KST 매일 새벽). scale-to-zero 정신은 §11.

---

## 5. REST API 명세

베이스: `/api/v1` · 포맷: JSON · 시간: ISO-8601(KST, `Asia/Seoul`) · 인증: §7 · 페이지네이션: `?page=&size=` (0-base).
> 향후 버전관리는 Spring Boot 3.5 네이티브 **API versioning**(헤더/경로/쿼리)으로 이행 가능. 해커톤은 경로 `/v1` 고정.

### 5.1 데이터 수집
**`POST /api/v1/ingest/pos`** — multipart `file`(CSV), `storeId`
```
CSV 헤더: business_date,category_code,quantity_sold,revenue,waste_quantity
Res 200: { "accepted": 360, "rejected": 2, "errors":[{"line":15,"reason":"INVALID_CATEGORY"}] }
```
**`POST /api/v1/ingest/inventory`** — multipart `file`, `storeId`
```
CSV 헤더: business_date,category_code,on_hand_quantity
```
**`POST /api/v1/weather/refresh`** — `{ "storeId":1, "date":"2026-06-27" }` → 기상청 즉시 수집 → `WeatherForecast`

### 5.2 분석 파이프라인
**`POST /api/v1/pipeline/run`**
```json
Req: { "storeId": 1, "targetDate": "2026-06-27" }
Res 202: { "storeId":1, "targetDate":"2026-06-27",
  "forecasted":8, "recommended":8, "carbonComputed":8,
  "modelVersion":"lgbm_global_v1", "elapsedMs":214 }
```
**`GET /api/v1/forecast?storeId=1&date=2026-06-27`**
```json
{ "storeId":1, "targetDate":"2026-06-27",
  "items":[{ "categoryCode":"MILK","p10":7.2,"p50":9.1,"p90":12.4,"modelVersion":"lgbm_global_v1" }] }
```
**`GET /api/v1/recommendations?storeId=1&date=2026-06-27`**
```json
{ "items":[{
  "categoryCode":"MILK","unit":"L",
  "recommendedQuantity":9.0,"optimalStockQuantity":11.0,"onHand":2.0,
  "baselineQuantity":12.0,"criticalRatio":0.42,
  "expectedWasteAvoidedKg":3.09,
  "rationale":{"reason":"rain_forecast","precipitationProb":80,"cu":1200,"co":1650}
}]}
```
**`GET /api/v1/recommendations/{id}`** — 단건 + 전체 rationale.

### 5.3 탄소 리포트
**`GET /api/v1/carbon/today?storeId=1`**
```json
{ "storeId":1,"targetDate":"2026-06-27",
  "guaranteedSavingKg":2.1,"potentialSavingKg":9.4,
  "byCategory":[{"categoryCode":"MILK","wasteAvoidedKg":3.09,"guaranteedSavingKg":0.6,"potentialSavingKg":9.3}],
  "carEquivalentKm":2.0 }
```
**`GET /api/v1/carbon/savings?storeId=1&from=2026-06-01&to=2026-06-26`** — 기간 일별 시계열.
**`GET /api/v1/carbon/savings/summary?storeId=1`** — 누적 총합·환산지표(대시보드 카드).

### 5.4 대시보드 집계
**`GET /api/v1/dashboard/summary?storeId=1`** — 발주 가이드 + 오늘 절감 + 예측정확도(WAPE) 한 번에.

### 5.5 sLLM 연동(언어 계층 프록시)
**`POST /api/v1/chat`**
```json
Req: { "storeId":1, "date":"2026-06-27", "question":"내일 우유 얼마나 시켜요?" }
Res: {
  "answer":"내일은 비 예보(강수확률 80%)라 우유 수요가 낮아 12L→9L 발주를 권장합니다. 과발주 3L를 줄여 약 9kgCO₂eq를 절감합니다.",
  "groundedOn":{ "forecastId":123,"recommendationId":456,"carbonSavingId":789 },
  "cacheHit": true, "llmLatencyMs": 180, "tokens": 142 }
```
**`GET /api/v1/chat/context?storeId=1&date=2026-06-27`** — (내부/③용) 산정 근거 컨텍스트 번들.
**백엔드 → sLLM 계약:**
```
POST {llm.sidecar.base-url}/generate
Req: { "question":"...", "grounding": { forecast, recommendation, carbon }, "locale":"ko" }
Res: { "answer":"...", "cacheHit":bool, "latencyMs":int, "tokens":int }
```
> `/chat`은 sLLM에 **근거 수치를 주입만** 하고 수치 생성을 맡기지 않는다(환각 차단). 시맨틱 캐싱·토큰 계측은 ③에서, 백엔드는 메트릭만 중계.

### 5.6 운영
- `GET /actuator/health`, `GET /actuator/metrics`, `GET /actuator/prometheus`(선택).
- `GET /swagger-ui.html`, `GET /v3/api-docs`.

### 5.7 표준 에러 포맷 (RFC 7807 호환 + code)
```json
{ "type":"about:blank","title":"Bad Request","status":400,
  "code":"INVALID_CSV","detail":"category_code 'XYZ' not found at line 15",
  "instance":"/api/v1/ingest/pos","timestamp":"2026-06-26T10:00:00+09:00" }
```
**에러 코드 카탈로그:** `INVALID_CSV`, `INVALID_CATEGORY`, `STORE_NOT_FOUND`, `FORECAST_UNAVAILABLE`, `WEATHER_FETCH_FAILED`, `LLM_UNAVAILABLE`, `PIPELINE_ALREADY_RUNNING`, `VALIDATION_ERROR`.
- `@RestControllerAdvice` + `ProblemDetail`(Spring 6) 사용.

---

## 6. 설정 (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zerowave
    username: ${DB_USER:zerowave}
    password: ${DB_PASSWORD:zerowave}
  jpa:
    hibernate.ddl-auto: validate        # 스키마는 Flyway가 소유
    properties.hibernate.jdbc.time_zone: Asia/Seoul
  flyway.enabled: true

external:
  kma:                                   # 기상청 단기예보
    base-url: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0
    service-key: ${KMA_SERVICE_KEY:}
forecast:
  mode: SIDECAR                          # SIDECAR | FALLBACK
  sidecar.base-url: ${FORECAST_URL:http://localhost:8000}
llm:
  sidecar.base-url: ${LLM_URL:http://localhost:8001}

optimization:
  default-cu: 1.0                        # 카테고리별 비용 미지정 시 기본
  default-co: 1.0
carbon:
  car-kgco2-per-km: 4.6                  # 승용차 환산 상수

resilience4j.circuitbreaker.instances.kma.failureRateThreshold: 50

management.endpoints.web.exposure.include: health,info,metrics,prometheus
```

---

## 7. 보안 / 인증
- 해커톤 기본: **API Key 헤더(`X-API-Key`)** 또는 단순 JWT(데모 사용자 1~2개). 현 `spring-boot-starter-security` 활용.
- 업로드(`/ingest/*`, `/pipeline/run`)는 인증 필수. 조회는 데모 토큰 허용.
- **CORS:** 프론트(Amplify 호스트) 오리진 화이트리스트.
- **CSV 업로드 검증:** 최대 크기 제한(`spring.servlet.multipart.max-file-size`), 헤더 스키마 검증, 행 단위 거부 리포트(부분 성공 허용).
- 시크릿(기상청 키 등)은 환경변수/`.env`로만 주입, 커밋 금지(`.gitignore` 확인).

---

## 8. 비기능 요구사항 & 저전력 계측 (idea_note §6)
'전부 LLM' 대비 본 설계의 우위를 **정량 메트릭**으로 입증.

| 메트릭(Micrometer) | 타입 | 의미 |
|---|---|---|
| `zerowave.llm.calls` | counter | sLLM 호출 횟수 |
| `zerowave.llm.tokens` | counter | 총 토큰 |
| `zerowave.llm.latency` | timer | 응답 지연(ms) |
| `zerowave.llm.cache.hit` / `.miss` | counter | 시맨틱 캐시 적중률 |
| `zerowave.pipeline.duration` | timer | 배치 1회 소요 |
| `zerowave.forecast.wape` | gauge(카테고리 태그) | 예측 정확도 |

- 배포 시 Micrometer → CloudWatch 연동(§11). 로컬은 `/actuator/metrics`·prometheus.
- **가용성:** 외부 API/모델 실패 시 폴백(예측 §4.2C, 날씨 캐시 재사용)으로 graceful degradation.
- **재현성:** 예측 피처·계수 버전을 결과에 스냅샷 저장.

---

## 9. 패키지 구조(제안)
```
com.netzero
├─ config       (SecurityConfig, WebConfig/CORS, OpenApiConfig, HttpClientConfig, SchedulingConfig)
├─ store        (Store, Category, repository)
├─ ingest       (PosCsvService, InventoryService, CsvParser, dto)
├─ weather      (KmaForecastClient[@HttpExchange], WeatherService, WeatherForecast)
├─ feature      (FeatureBuilder, HolidayCalendar)
├─ forecast     (DemandForecastService, ForecastSidecarClient, StatsFallbackForecaster)
├─ order        (OrderOptimizationService, Newsvendor, QuantileInterpolator)
├─ carbon       (CarbonAccountingService, EmissionFactor, repository)
├─ pipeline     (DailyPipelineService, PipelineScheduler, PipelineController)
├─ chat         (ChatProxyController, RagContextAssembler, LlmSidecarClient)
├─ dashboard    (DashboardService, DashboardController)
└─ common       (error[ProblemDetail advice], metrics, dto, BaseEntity)
```

---

## 10. 구현 마일스톤(해커톤)
1. **M0 — 의존성/빌드 갱신:** §2.2 적용(Spring Boot 3.5.13, springdoc 2.8.17, RestClient, Flyway, Actuator). 부팅 확인.
2. **M1 — 골격:** Flyway 스키마+시드(배출계수/카테고리/데모매장), `/ingest/pos`, 합성데이터 적재.
3. **M2 — 산정 코어:** 통계 폴백 예측 → newsvendor 발주 → 탄소 산정 → `/recommendations`,`/carbon/today`. (LLM 없이 end-to-end)
4. **M3 — 외부연동:** 기상청 수집(@HttpExchange), 피처 반영, `/pipeline/run`, `/dashboard/summary`.
5. **M4 — 언어계층:** `/chat` 프록시 + RAG 근거 번들 + 계측 메트릭.
6. **M5 — 정확도/시연:** LightGBM 사이드카 교체, WAPE 로깅, 데모 시나리오.

---

## 11. 배포 / 인프라 (idea_note 대안)
| 항목 | 해커톤 로컬·시연 | idea_note 목표 아키텍처 |
|------|------------------|--------------------------|
| 실행 | 로컬/EC2 Spring Boot | EC2 1대 ETL + Lambda+EventBridge 배치 |
| 저장소 | PostgreSQL 17 | DynamoDB(정제데이터·산정로그) |
| 배치 | `@Scheduled` cron | EventBridge(scale-to-zero 저전력) |
| sLLM | Python FastAPI 사이드카 | EC2+Inferentia(Inf2) 서빙 |
| 계측 | Micrometer/Actuator | CloudWatch 대시보드 |
| 프레임워크 | Spring Boot 3.5.13 | (장기) Spring Boot 4.0.x + springdoc 3.0.x |

> 해커톤은 **JPA/PostgreSQL 단일 백엔드로 end-to-end 시연**하고, DynamoDB/Lambda는 Repository 추상화·배치 트리거 분리로 이행 여지만 남긴다.

---

## 부록 A. 산정식 요약
- 신문팔이: `CR = Cu/(Cu+Co)`, `Q* = F⁻¹(CR)`(p10/p50/p90 구간선형보간), 실발주 `= max(0, Q*−재고)`
- 폐기 절감: `ΔQ = max(0, baseline − Q*)` → `×densityKgPerUnit` 로 kg 환산
- 탄소: `ΔE = Σ ΔQᵢ × (EF_prod,ᵢ + EF_waste,ᵢ)` / 보장 = `Σ ΔQᵢ×EF_waste,ᵢ`, 잠재 = 전체
- 정확도: `WAPE = Σ|actual−pred| / Σactual`

## 부록 B. 이번 개정(v2)에서 바로잡은 outdated 항목
1. **HTTP 클라이언트**: WebFlux+WebClient → **RestClient/`@HttpExchange`** (리액티브 의존성 제거).
2. **Spring Boot**: 3.5.3 → **3.5.13**(최신 패치), 4.0.x 이행 옵션 명시.
3. **springdoc-openapi**: 2.6.0 → **2.8.17**(Spring Boot 3.5 호환).
4. **PostgreSQL**: 16 → **17**.
5. 에러 응답: 자체 포맷 → **`ProblemDetail`(RFC 7807)** + code 카탈로그.
6. Actuator/Micrometer 메트릭·resilience4j·API versioning 등 현행 권장 패턴 반영.
