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

### 1.4 개발 수준 — **Demo (not Prod)**
해커톤 시연 목표. 단순성·동작 우선, 운영 견고성은 후순위.
- **단일 매장 가정**: Demo는 store 1개만 존재(스키마엔 `store_id` 유지하되 시드/조회는 단일 매장).
- **간소화 허용**: 인증은 단순 API Key/데모 토큰, 합성 CSV 기반 데이터, 스케줄 대신 수동 트리거 위주, 인메모리/단일 인스턴스.
- **생략**: 멀티테넌시·수평확장·고가용·정교한 권한·감사로그 등 Prod 관심사는 §11 이행과제로만 표기.
- 결정적 산정식·계측 등 **데모 메시지에 직접 쓰이는 정확성**은 타협하지 않는다.

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
    implementation platform('software.amazon.awssdk:bom:2.31.0')              // 추가(S3 BOM)
    implementation 'software.amazon.awssdk:s3'                                 // 추가(월말 CSV 업로드)
    implementation 'software.amazon.awssdk:s3-transfer-manager'               // 선택(대용량/멀티파트)
    // S3Presigner는 s3 모듈에 포함(presigned URL 발급)
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
ItemMaster 1───* SalesRecord *───1 Store
           1───* InventorySnapshot   *───1 Store
           1───* OrderPolicy         *───1 Store
           1───* DemandForecast      *───1 Store
           1───* OrderRecommendation *───1 Store
           1───* CarbonSaving        *───1 Store
WeatherForecast (region·date 단위)
```
> **ItemMaster가 마스터 테이블**: 품목 메타데이터 + 배출계수(EF_prod/EF_waste) + 단가를 모두 보유.
> 기존 `Category`/`EmissionFactor` 테이블은 **ItemMaster로 통합**(EF를 별도 버전테이블에서 분리하지 않고 품목 행에 직접 적재).

### 3.2 엔티티 정의 (필드·타입·제약)

#### Store — 매장
| 필드 | 타입 | 제약/설명 |
|------|------|------|
| id | Long PK | identity |
| name | varchar(100) | NOT NULL |
| region | varchar(40) | 기상청 지역코드 매핑 |
| nx, ny | int | 기상청 격자 좌표 |
| createdAt | timestamptz | default now() |

#### ItemMaster — 품목 마스터 (구 Category + EmissionFactor 통합)
| 필드(한글) | 컬럼 | 타입 | 제약/설명 |
|------|------|------|------|
| 품목ID | id | Long PK | identity |
| 품목명 | name | varchar(80) | NOT NULL |
| 구분 | category | varchar(12) | **CHECK** ∈ {`완제품`,`원재료`,`판매음료`,`소모품`} |
| 폐기대상 | wasteTarget | boolean | 폐기·탄소 산정 대상 여부(소모품 등은 false) |
| 관리단위 | unit | varchar(12) | 예: `ea`,`L`,`kg`,`box` |
| 유통기한 | shelfLifeDays | int | 일 단위(신문팔이 단일기간 판단) |
| 보관조건 | storageCondition | varchar(6) | **CHECK** ∈ {`상온`,`냉장`,`냉동`} |
| kg 환산 | kgPerUnit | numeric(10,4) | 관리단위 → kg 환산계수 |
| 생산배출계수 | efProd | numeric(8,3) | kgCO₂eq/kg (Poore & Nemecek 2018) |
| 폐기배출계수 | efWaste | numeric(8,3) | kgCO₂eq/kg |
| 매입단가 | purchasePrice | numeric(12,2) | 원 |
| 단가단위 | priceUnit | varchar(12) | 매입단가 기준 단위(예: `원/L`,`원/ea`) |
| 비고/출처 | note | text | **자연어(한글)** — 계수 출처·가정 등 |
> EF는 품목 행에 직접 보유(버전테이블 미사용). 비폐기 품목(소모품)은 `wasteTarget=false`로 탄소 산정에서 제외.

#### SalesRecord — 일별 품목 판매(actual, 합성/POS)
| 필드(한글) | 컬럼 | 타입 | 제약/설명 |
|------|------|------|------|
| ID | id | Long PK | |
| (매장) | store_id | FK | NOT NULL (다매장 무결성용, per-store CSV엔 미노출) |
| 날짜 | businessDate | date | NOT NULL |
| 요일 | dayOfWeek | varchar(3) | `월`~`일` (저장; 파생 가능) |
| 날씨 | weather | varchar(4) | **CHECK** ∈ {`맑음`,`흐림`,`비`} |
| 평균기온 | avgTemp | numeric(4,1) | ℃ (sales.csv `기온` 컬럼 원천) |
| 강수mm | precipitationMm | numeric(6,2) | |
| 행사 | event | varchar(40) | nullable(프로모션/행사명) |
| 신메뉴 | newMenu | varchar(40) | nullable(신메뉴 표기/명) |
| 구분 | category | varchar(12) | ItemMaster.category 비정규화(필터·추출용) |
| 품목 | item_id | FK→ItemMaster | NOT NULL |
| 판매수량 | quantitySold | numeric(12,3) | ≥0 |
| 비고/시나리오 | scenarioNote | text | nullable(sales.csv `비고_시나리오` 원천) |
> **UNIQUE(store_id, item_id, businessDate)**. 기존 revenue/wasteQuantity/source 필드는 제거(폐기·재고흐름은 InventorySnapshot/추출에서 계산).

#### InventorySnapshot — 일별 재고 원장 (full daily ledger)
일·품목별 재고 흐름을 **계산 결과까지 적재**한다. `store-inventory.csv` 추출은 이 테이블의 직접 투영(§5.8).
| 필드(한글) | 컬럼 | 타입 | 제약/설명 |
|------|------|------|------|
| ID | id | Long PK | |
| (매장) | store_id | FK | NOT NULL (Demo: 단일 매장) |
| 날짜 | businessDate | date | NOT NULL |
| 요일 | dayOfWeek | varchar(3) | `월`~`일`(파생 저장) |
| 품목 | item_id | FK→ItemMaster | NOT NULL |
| 구분 | category | varchar(12) | ItemMaster 비정규화 |
| 단위 | unit | varchar(12) | ItemMaster 비정규화 |
| 발주 | orderedQty | numeric(12,3) | **발주주기에 맞는 발주일이면 발주량(숫자), 아니면 0** |
| 기초재고 | openingStock | numeric(12,3) | = 전일 기말재고 |
| 수요 | demand | numeric(12,3) | 잠재수요(실판매+결품) |
| 실판매 | actualSales | numeric(12,3) | 실제 판매량 |
| 결품 | stockout | numeric(12,3) | `max(0, 수요 − (기초재고+발주))` |
| 폐기 | wasteQty | numeric(12,3) | 유통기한 경과분 |
| 기말재고 | closingStock | numeric(12,3) | `기초재고 + 발주 − 실판매 − 폐기` (newsvendor 현재고로 사용) |
| 폐기_kg | wasteKg | numeric(12,3) | `폐기 × ItemMaster.kgPerUnit` |
| 탄소_kgCO2e | wasteCarbonKg | numeric(12,3) | `폐기_kg × (efProd+efWaste)` (wasteTarget=true만, 아니면 0) |
| 폐기_비용_원 | wasteCostKrw | numeric(14,2) | `폐기 × ItemMaster.purchasePrice` |
| 마지막발주일 | lastOrderDate | date | **직전에 `orderedQty>0`였던 날짜** — 발주주기 도래 판정 기준 |
> **UNIQUE(store_id, item_id, businessDate)**. 산출 컬럼(결품·폐기·폐기_kg·탄소·비용)은 파이프라인/합성생성기가 적재(§5.8 동일 규칙).
> **발주일 판정:** `orderedQty`는 발주일에만 양수, 비발주일은 0. `lastOrderDate`는 가장 최근 `orderedQty>0`인 날을 이월 기록하여 `오늘 − lastOrderDate ≥ orderCycleDays`로 다음 발주일을 판단한다.

#### OrderPolicy — 품목별 발주 정책 (신규)
| 필드(한글) | 컬럼 | 타입 | 제약/설명 |
|------|------|------|------|
| (매장) | store_id | FK | NOT NULL |
| 품목ID | item_id | FK→ItemMaster | NOT NULL |
| 품목명 | itemName | varchar(80) | ItemMaster.name 비정규화 |
| 구분 | category | varchar(12) | 비정규화 |
| 발주방식 | orderMethod | varchar(16) | 예: `정기발주`,`정량발주`,`신문팔이` |
| 발주주기 | orderCycleDays | int | 일 단위 |
| 리드타임 | leadTimeDays | int | 일 단위 |
| 안전재고_z | safetyZ | numeric(5,2) | 안전재고 z값(서비스수준 방향) |
| 발주단위 | orderLotUnit | numeric(12,3) | 발주 묶음 단위(올림 반올림 기준) |
| 비고 | note | text | nullable |
> **UNIQUE(store_id, item_id)**. 발주 최적화 시 lot 단위 올림·리드타임 보정에 사용.

#### WeatherForecast — 기상청 예보(피처 원천)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| region | varchar(40) | |
| forecastDate | date | 예보 대상일(=익일) |
| tempMax, tempMin | numeric(4,1) | ℃ (기상청 원시) |
| avgTemp | numeric(4,1) | **평균온도** = `(tempMax+tempMin)/2`. **AI 예측 요청(WeatherSnapshot)에 전달하는 기온 필드** |
| precipitationMm | numeric(6,2) | 강수량 |
| precipitationProb | int | 강수확률 % (0~100) |
| skyCode | int | 하늘상태(1맑음~4흐림) |
| fetchedAt | timestamptz | 수집 시각 |
> UNIQUE(region, forecastDate, fetchedAt) — 동일일 다회 수집 이력 보존, 조회는 최신 fetchedAt.
> 예측 요청의 `weather.avgTemp`는 이 `avgTemp`(평균온도)를 사용한다(tempMax/tempMin 직접 전달 안 함).

#### DemandForecast — 수요예측 결과
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, item_id | FK→ItemMaster | |
| targetDate | date | 예측 대상일(익일) |
| predictedQuantity | numeric(12,3) | 점추정(p50) |
| p10, p50, p90 | numeric(12,3) | 분위 예측(신문팔이 입력) |
| modelVersion | varchar(40) | 예: `lgbm_global_v1`, `baseline_v1`(AI 폴백) |
| features | jsonb | 사용 피처 스냅샷(재현성·RAG) |
> UNIQUE(store, item, targetDate) — upsert.

#### OrderRecommendation — 발주 추천
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, item_id | FK→ItemMaster | |
| targetDate | date | |
| recommendedQuantity | numeric(12,3) | 신문팔이 최적 발주량 후 재고차감 실발주 |
| optimalStockQuantity | numeric(12,3) | Q* (재고차감 전 목표재고) |
| baselineQuantity | numeric(12,3) | 비교 기준(관행 발주) |
| criticalRatio | numeric(6,4) | Cu/(Cu+Co) |
| expectedWasteAvoidedKg | numeric(12,3) | baseline 대비 줄인 예상 폐기량(kg) |
| rationale | jsonb | 산출 근거(피처·분위·비용) → RAG 근거 |
> UNIQUE(store, item, targetDate).

#### CarbonSaving — 탄소 절감 산정
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long PK | |
| store_id, item_id | FK→ItemMaster | |
| targetDate | date | |
| wasteAvoidedKg | numeric(12,3) | ΔQ(kg) |
| guaranteedSavingKg | numeric(12,3) | 최소보장(처리 회피, EF_waste) |
| potentialSavingKg | numeric(12,3) | 잠재(생산+처리, EF_prod+EF_waste) |
| efProdSnapshot, efWasteSnapshot | numeric(8,3) | 산정 시점 ItemMaster EF 스냅샷(재현성) |
> UNIQUE(store, item, targetDate). EF는 ItemMaster에서 조회하되 산정값을 스냅샷 저장.

### 3.3 Flyway 마이그레이션 구성
```
src/main/resources/db/migration/
  V1__schema.sql            -- 전체 테이블 DDL + 인덱스/제약
  V2__seed_item_master.sql  -- 품목 마스터(구분·유통기한·단위·kgPerUnit·EF·단가·비고)
  V3__seed_store_demo.sql   -- 데모 매장(기상청 격자 포함)
  V4__seed_order_policy.sql -- 품목별 발주 정책
```
인덱스 권장: 결과 테이블에 `(store_id, target_date)` / SalesRecord·InventorySnapshot에 `(store_id, item_id, business_date)`.
EF 시드 예시(ItemMaster): 소고기 efProd≈60, 치즈≈21, 닭≈6, 돼지≈7, 우유≈3.

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
    avgTemp(평균온도), ma7(최근7일 이동평균), trend, storeId, itemId]
```
- 공휴일은 정적 테이블(또는 공휴일 API)로 판정.
- 피처는 `DemandForecast.features(jsonb)`에 스냅샷 저장(재현성·RAG 근거).

### 4.2 수요예측 (DemandForecastService)
- **모델:** 전 매장 통합 글로벌 모델 1개(LightGBM), **Python AI 서버**가 서빙. 매장ID·품목을 피처로 사용.
- **연동:** Spring이 `ForecastPort`(RestClient 구현)로 피처+날씨를 전달 → AI 서버가 분위예측 반환(상태없는 추론). 상세 경계는 설계문서 `docs/superpowers/specs/2026-06-26-zerowave-backend-design.md` 참고.
- **폴백:** LightGBM 미준비/호출실패 시 **AI 서버**가 ma7 기반 `baseline_v1`을 반환(폴백은 AI 책임). Spring은 자체 예측 폴백 미보유. 하드 실패 시 `FORECAST_UNAVAILABLE`.
- **출력:** 커버기간(리드타임+발주주기)의 **일별** 분위예측 배열 `daily[].{p10,p50,p90}`.
- **정확도 관리:** WAPE = Σ|actual−pred| / Σactual 을 품목별 산출·로깅. 신선식품은 목표 차등.

**커버기간 개념(발주주기·리드타임):** 지금 발주하면 **리드타임** 뒤 입고되어 **다음 발주분이 들어올 때까지(=발주주기)** 버텨야 한다. 따라서 충당해야 할 수요 = **리드타임+발주주기 일수**의 합. `OrderPolicy.leadTimeDays`, `OrderPolicy.orderCycleDays`를 예측 요청에 함께 전달해 AI가 그 기간만큼 일별 예측을 반환하면, 백엔드가 **합산**해 발주량을 구한다.

**예측 서비스 계약** — `POST {ai.base-url}/v1/order-recommendation` (발주용 커버기간 예측; 날씨·커버기간은 store·date 공통이라 top-level 분리):
> 백엔드는 정형 피처와 함께 **판매내역 CSV의 S3 presigned GET URL**(`salesHistory.presignedUrls`, 최근 `ai.sales-history-months`개월)을 첨부한다. AI가 다운로드해 추론에 사용(§5.9, ai_server_api_spec.md §3.5).
> 발주와 무관한 단순 익일 수요는 별도 엔드포인트 `POST {ai.base-url}/v1/forecast`(단일일 분위)를 사용한다. 상세는 [`ai_server_api_spec.md`](./ai_server_api_spec.md) §3·§4.
```
Req:  { "storeId":1, "targetDate":"2026-06-27",
        "coverage":{ "leadTimeDays":1, "orderCycleDays":7, "coverageDays":8 },
        "weather":[ {"forecastDate":"2026-06-27","avgTemp":21.2,
                     "precipitationMm":12.0,"precipitationProb":80,"skyCode":4}, ... ],  // 커버기간 일수만큼, 기온=평균온도
        "rows":[{ "itemId":101,"orderCycleDays":7,"leadTimeDays":1,
                  "features":{"dayOfWeek":6,"isHoliday":false,"ma7":9.4,"trend":-0.3} }] }
Res:  { "modelVersion":"lgbm_global_v1",
        "predictions":[{ "itemId":101,
          "daily":[ {"date":"2026-06-28","p10":7.2,"p50":9.1,"p90":12.4}, ... ] }] }  // coverageDays개
```
> Demo 단순화: 미래 일자 날씨가 부족하면 `weather`는 가용분만 전달하고 AI가 결측 보정. 일별 배열을 못 주는 모델이면 단일 집계 분위(`p10/p50/p90` 단일)로도 허용(백엔드가 그대로 사용).

### 4.3 발주 최적화 (OrderOptimizationService) — 신문팔이(Newsvendor) + 커버기간 합산
유통기한 짧은 품목에 표준 안전재고 대신 newsvendor 적용하되, **커버기간(리드타임+발주주기) 합산 수요**를 대상으로 한다.

```
커버기간:        H = leadTimeDays + orderCycleDays           # 예: 1 + 7 = 8일
기간 합산 분위:   P10ₕ=Σ daily.p10,  P50ₕ=Σ daily.p50,  P90ₕ=Σ daily.p90   # 7일치 총합
임계분위:        CR = Cu / (Cu + Co)
  Cu = 과소(결품) 단위비용 = 판매단가 − 원가
  Co = 과잉(폐기) 단위비용 = 원가 + 폐기처리비 (+ 탄소페널티 옵션)
최적 목표재고:   Q* = F⁻¹ₕ(CR)        # 기간합산 분위(P10ₕ/P50ₕ/P90ₕ) 구간선형보간
실 발주량:       order = roundToLot( max(0, Q* − 현재고), orderLotUnit )   # 현재고=최신 기말재고
```
- **분위 보간(F⁻¹ 근사):** 기간합산 P10ₕ/P50ₕ/P90ₕ 세 점을 **구간선형보간**.
  - CR ≤ 0.10 → P10ₕ 이하 외삽, 0.10<CR≤0.50 → (P10ₕ,P50ₕ), 0.50<CR≤0.90 → (P50ₕ,P90ₕ), CR>0.90 → P90ₕ 이상 외삽.
- **합산 주의(Demo 근사):** 분위는 엄밀히는 가산이 아니나, Demo에서는 일별 분위를 단순 합산(comonotone 가정)해 충분. 단일 집계 분위를 받은 경우 그대로 사용.
- **발주단위 올림:** `OrderPolicy.orderLotUnit`로 묶음 올림(예: 6개 단위).
- **자동 보수화:** 폐기비용 Co 큰 품목일수록 CR↓ → Q*↓ → 발주 보수화(idea_note 핵심).
- **ΔQ(예상 폐기 회피량):** `expectedWasteAvoided = max(0, baselineQuantity − Q*)` 를 kg 환산(`ItemMaster.kgPerUnit`).
  - `baselineQuantity`는 관행 발주 추정(과거 동요일 평균 × 커버기간) — 시연 기본.

```java
int H = policy.leadTimeDays() + policy.orderCycleDays();          // 커버기간
double p10h = sum(daily, F::p10), p50h = sum(daily, F::p50), p90h = sum(daily, F::p90); // 기간 합산
double criticalRatio = cu / (cu + co);
double qStar = quantileInterpolate(p10h, p50h, p90h, criticalRatio);
double order = roundToLot(Math.max(0, qStar - closingStock), policy.orderLotUnit());
double wasteAvoidedKg = Math.max(0, baseline - qStar) * item.kgPerUnit();
```

### 4.4 탄소 산정 (CarbonAccountingService)
산정식(idea_note §3):
```
ΔE = Σᵢ ΔQᵢ(kg) × (EF_prod,ᵢ + EF_waste,ᵢ)
```
- EF는 `ItemMaster`(efProd, efWaste)에서 O(1) 조회. `wasteTarget=false` 품목은 제외.
- **두 라벨 분리 보고:**
  - `guaranteedSavingKg = Σ ΔQᵢ × EF_waste,ᵢ` — 최소 보장(폐기 처리 회피, 확실).
  - `potentialSavingKg  = Σ ΔQᵢ × (EF_prod,ᵢ + EF_waste,ᵢ)` — 잠재(상류 생산 회피 포함).
- 환산: 관리단위(L/ea)→kg는 `ItemMaster.kgPerUnit`로 표준화.
- 대시보드 보조: 승용차 환산 ≈ `potentialSavingKg / 4.6kg(승용차 km당 평균)` 등 표시지표(상수는 config).

### 4.5 일일 배치 오케스트레이션 (DailyPipelineService)
하루 1회 실행(idea_note: Lambda+EventBridge → 해커톤은 `@Scheduled` + 수동 트리거):
```
0) 발주 도래 품목 선별    → OrderPolicy + InventorySnapshot.lastOrderDate
                          (date − lastOrderDate ≥ orderCycleDays 인 품목만, §5.2)
1) 기상청 예보 수집(커버기간) → WeatherForecast
2) 피처 생성              → FeatureBuilder
3) 품목별 수요예측        → DemandForecast (AI 서버 ForecastPort, 커버기간 일별, 폴백도 AI)
4) 신문팔이 발주 최적화   → OrderRecommendation (커버기간 합산)
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

> 요청/응답 필드 JSON 예시와 **응답 envelope(`{success,data}`)** 의 권위 기준은 [`backend_api_spec.md`](./backend_api_spec.md). 본 절은 요약.

### 5.1 데이터 수집
**`POST /api/v1/ingest/sales`** — multipart `file`(CSV), `storeId` (대량·풀컬럼)
```
CSV 헤더: 날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오
data: { "accepted": 360, "rejected": 2, "errors":[{"line":15,"code":"ITEM_NOT_FOUND","value":"없는품목"}] }
```
**`POST /api/v1/ingest/sales/daily`** — multipart `file`(CSV), `storeId`, body `eventFlag`,`newMenuFlag`,`scenarioNote` (하루치)
```
CSV 헤더: 날짜,요일,품목,구분,판매수량        (날씨 컬럼 없음 → WeatherForecast 백필 또는 null)
body 메타(그날 전체 적용): eventFlag(bool), newMenuFlag(bool), scenarioNote(str, 예 "주말+맑음->수요 높음")
저장: eventFlag/newMenuFlag → SalesRecord.event/newMenu 마커("Y"/null), scenarioNote → SalesRecord.scenarioNote
data: { "appliedDate":"2026-06-28","accepted":2,"rejected":0,"eventFlag":false,"newMenuFlag":true,"errors":[] }
```
**`POST /api/v1/ingest/inventory`** — multipart `file`, `storeId`
```
CSV 헤더: 날짜,품목,구분,단위,발주,기초재고,수요,실판매,결품,폐기,기말재고
```
**`POST /api/v1/weather/refresh`** — `{ "storeId":1, "date":"2026-06-27" }` → 기상청 즉시 수집 → `WeatherForecast`
> 품목은 `품목ID` 또는 `품목명`으로 매칭(ItemMaster). 미존재 시 행 거부(`ITEM_NOT_FOUND`).

### 5.2 분석 파이프라인
**`POST /api/v1/pipeline/run`**
```json
Req: { "storeId": 1, "targetDate": "2026-06-27" }
Res 202: { "storeId":1, "targetDate":"2026-06-27",
  "forecasted":8, "recommended":8, "carbonComputed":8,
  "modelVersion":"lgbm_global_v1", "elapsedMs":214 }
```
**`GET /api/v1/forecast?storeId=1&date=2026-06-27`**
**OrderPolicy·InventorySnapshot를 참조해 해당일 발주가 도래(due)한 품목만** 골라 커버기간(리드타임+발주주기)만큼 예측·반환.
- **발주 도래 판정(품목별):** `date − InventorySnapshot.lastOrderDate ≥ OrderPolicy.orderCycleDays` (lastOrderDate 없으면 최초 발주로 간주해 도래). OrderPolicy 미존재 품목은 제외.
- 도래 품목 각각에 대해 `coverage = leadTimeDays + orderCycleDays` 일별 예측을 AI에 요청하고, 합산 분위(`p10ₕ/p50ₕ/p90ₕ`)와 일별 배열을 함께 반환.
```json
{ "storeId":1, "targetDate":"2026-06-27",
  "dueItems":[{
    "itemId":101,"itemName":"우유",
    "coverage":{"leadTimeDays":1,"orderCycleDays":7,"coverageDays":8},
    "lastOrderDate":"2026-06-19","daysSinceLastOrder":8,
    "horizonForecast":{"p10":60,"p50":80,"p90":108},
    "daily":[{"date":"2026-06-28","p10":6,"p50":8,"p90":11}, "..."],
    "modelVersion":"lgbm_global_v1"
  }],
  "skipped":[{ "itemId":205,"itemName":"원두","reason":"NOT_DUE","daysSinceLastOrder":3,"orderCycleDays":14 }] }
```
> `POST /pipeline/run`도 동일한 **발주 도래 품목 선별** 로직을 사용한다(도래 품목만 예측→발주→탄소 산정).
**`GET /api/v1/recommendations?storeId=1&date=2026-06-27`**
```json
{ "items":[{
  "itemId":101,"itemName":"우유","unit":"L",
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
  "byItem":[{"itemId":101,"itemName":"우유","wasteAvoidedKg":3.09,"guaranteedSavingKg":0.6,"potentialSavingKg":9.3}],
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
POST {ai.base-url}/v1/generate
Req: { "question":"...", "grounding": { forecast, recommendation, carbon }, "locale":"ko" }
Res: { "answer":"...", "cacheHit":bool, "latencyMs":int, "tokens":int }
```
> `/chat`은 sLLM에 **근거 수치를 주입만** 하고 수치 생성을 맡기지 않는다(환각 차단). 시맨틱 캐싱·토큰 계측은 ③에서, 백엔드는 메트릭만 중계.

### 5.6 운영
- `GET /actuator/health`, `GET /actuator/metrics`, `GET /actuator/prometheus`(선택).
- `GET /swagger-ui.html`, `GET /v3/api-docs`.

### 5.7 응답 Envelope (성공/오류 일관 구조)
모든 JSON 응답은 envelope으로 감싼다(상세·전체 코드표는 [`backend_api_spec.md`](./backend_api_spec.md) §1).
```json
// 성공
{ "success": true, "data": { /* payload */ } }
// 오류
{ "success": false, "error": { "code": "CONTENT_NOT_FOUND", "message": "파일을 찾을 수 없습니다." } }
```
**에러 코드 카탈로그:** `VALIDATION_ERROR`, `INVALID_CSV`, `ITEM_NOT_FOUND`, `STORE_NOT_FOUND`, `CONTENT_NOT_FOUND`, `FORECAST_UNAVAILABLE`, `WEATHER_FETCH_FAILED`, `LLM_UNAVAILABLE`, `PIPELINE_ALREADY_RUNNING`, `INTERNAL_ERROR`.
- 구현: `ApiResponse<T>` 래퍼 + `@RestControllerAdvice`. HTTP 상태코드는 code에 매핑. CSV 다운로드·actuator는 envelope 미적용.

### 5.8 데이터 추출 (CSV/엑셀 내보내기)
월 단위(1개월) 추출. `Content-Type: text/csv; charset=UTF-8`(BOM 포함, 엑셀 한글 호환), 파일명은 `Content-Disposition`. `format=csv|xlsx`(기본 csv, xlsx는 Apache POI 옵션). 구현은 `export` 도메인의 `ExportService` + 스트리밍 응답.

**① 판매내역 — `GET /api/v1/export/sales.csv?storeId=1&month=2026-06`**
SalesRecord를 그대로 투영(매장 단위, 월 범위).
```
컬럼: 날짜, 요일, 날씨, 기온, 강수mm, 행사, 신메뉴, 품목, 구분, 판매수량, 비고_시나리오
원천: SalesRecord (+ 품목명/구분은 ItemMaster 조인)
```

**② 재고 흐름 — `GET /api/v1/export/store-inventory.csv?storeId=1&month=2026-06`**
**InventorySnapshot(일별 재고원장)의 직접 투영** — 모든 컬럼이 테이블에 적재되어 있어 추출 시 추가 계산 불필요.
```
컬럼: 날짜, 요일, 품목, 구분, 단위, 발주, 기초재고, 수요, 실판매, 결품, 폐기, 기말재고, 폐기_kg, 탄소_kgCO2e, 폐기_비용_원
원천: InventorySnapshot 전 컬럼 (품목명은 ItemMaster 조인)
```
> 산출 컬럼(결품·폐기·폐기_kg·탄소·비용)은 InventorySnapshot 적재 시 계산됨(§3.2 InventorySnapshot 규칙 참조).

### 5.9 월말 S3 아카이빙 (MonthlyExportScheduler)
**매달 말일**에 해당 월(1개월치) **판매내역·재고관리내역 CSV를 생성해 S3에 저장**한다. 위 §5.8과 동일 컬럼 규약을 재사용.
- **스케줄:** `@Scheduled`(cron, KST 매월 말일 자정 무렵). Demo는 `POST /api/v1/export/archive?storeId=&month=` 수동 트리거도 제공.
- **산출물·키 규칙(S3):**
  - 판매내역 → `s3://{bucket}/sales/store{storeId}/sales-{YYYY-MM}.csv`
  - 재고관리 → `s3://{bucket}/inventory/store{storeId}/store-inventory-{YYYY-MM}.csv`
- **용도:** ① 데이터 아카이브, ② **AI 예측 요청 시 판매내역 CSV의 presigned GET URL을 생성해 전달**(AI가 추론모델 입력으로 사용 — ai_server_api_spec.md §3.5).
- **presigned URL:** `S3Presigner`로 읽기전용·시간제한(예: 10분) GET URL 발급. 예측 호출(`ForecastPort`) 직전 최근 N개월 sales CSV에 대해 생성해 `salesHistory.presignedUrls`로 첨부.
- **Demo/로컬:** 실제 S3 또는 **S3 호환(MinIO/LocalStack)**. 버킷·자격증명은 env로 주입.
- **구현:** `export` 도메인의 `S3ArchiveService`(업로드) + `PresignService`(URL 발급).

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
ai:                                      # Python AI 서버 (수요예측 + sLLM, 별도 레포)
  base-url: ${AI_SERVER_URL:http://localhost:8000}
  order-recommendation-path: /v1/order-recommendation   # 발주용 커버기간 예측
  forecast-path: /v1/forecast                           # 익일 단일 수요예측
  generate-path: /v1/generate
  sales-history-months: 3                                # 예측 요청에 첨부할 최근 판매 CSV 개월 수

storage:                                 # S3(또는 MinIO/LocalStack) 월말 아카이빙·presigned URL
  s3:
    bucket: ${S3_BUCKET:zerowave}
    region: ${AWS_REGION:ap-northeast-2}
    endpoint: ${S3_ENDPOINT:}            # MinIO/LocalStack 사용 시 지정(비우면 실제 S3)
    presign-expiry-seconds: 600          # presigned GET URL 유효시간(10분)

optimization:
  default-cu: 1.0                        # 품목별 비용 미지정 시 기본
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
- **가용성:** 외부 API/모델 실패 시 폴백(예측은 AI 서버 baseline, 날씨 캐시 재사용)으로 graceful degradation.
- **재현성:** 예측 피처·계수 버전을 결과에 스냅샷 저장.

---

## 9. 패키지 구조(제안)
도메인별 `controller/dto/domain/service/repository` 표준 레이어 + 외부 경계(weather·forecast·chat)에만 `port`. 상세는 설계문서 §3.
```
com.netzero
├─ config       (SecurityConfig, WebConfig/CORS, OpenApiConfig, HttpClientConfig, SchedulingConfig)
├─ store        (Store, ItemMaster, OrderPolicy, repository)
├─ ingest       (SalesCsvService, InventoryService, CsvParser, dto)
├─ weather      (port: KmaForecastPort+KmaForecastClient[@HttpExchange], WeatherService, WeatherForecast)
├─ feature      (FeatureBuilder, HolidayCalendar)
├─ forecast     (port: ForecastPort+AiForecastClient[RestClient], DemandForecastService)  ← 자체 폴백 없음
├─ order        (OrderOptimizationService, Newsvendor, QuantileInterpolator)
├─ carbon       (CarbonAccountingService, repository)  ← EF는 ItemMaster
├─ export       (ExportService, SalesCsvExporter, InventoryFlowExporter, S3ArchiveService, PresignService, MonthlyExportScheduler, ExportController)
├─ pipeline     (DailyPipelineService, PipelineScheduler, PipelineController)
├─ chat         (port: LlmPort+AiLlmClient[RestClient], ChatService, RagContextAssembler, ChatController)
├─ dashboard    (DashboardService, DashboardController)
└─ common       (ApiResponse envelope, error[advice], metrics, dto, BaseEntity)
```

---

## 10. 구현 마일스톤(해커톤)
1. **M0 — 의존성/빌드 갱신:** §2.2 적용(Spring Boot 3.5.13, springdoc 2.8.17, RestClient, Flyway, Actuator). 부팅 확인.
2. **M1 — 골격:** Flyway 스키마+시드(ItemMaster/데모매장/OrderPolicy), `/ingest/sales`·`/ingest/inventory`, 합성데이터 적재, `/export/*` 추출.
3. **M2 — 산정 코어:** newsvendor 발주 → 탄소 산정 → `/recommendations`,`/carbon/today`. (AI 서버 목으로 end-to-end)
4. **M3 — 외부연동:** 기상청 수집(@HttpExchange), 피처 반영, ForecastPort 실연동(판매CSV presigned URL 첨부), S3 월말 아카이빙(`MonthlyExportScheduler`), `/pipeline/run`, `/dashboard/summary`.
5. **M4 — 언어계층:** `/chat` 프록시 + RAG 근거 번들 + 계측 메트릭.
6. **M5 — 정확도/시연:** LightGBM AI 서버 통합, WAPE 로깅, 데모 시나리오.

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
- 커버기간 합산: `H = leadTimeDays + orderCycleDays`, `P··ₕ = Σ daily.p··`
- 신문팔이: `CR = Cu/(Cu+Co)`, `Q* = F⁻¹ₕ(CR)`(P10ₕ/P50ₕ/P90ₕ 구간선형보간), 실발주 `= roundToLot(max(0, Q*−현재고), orderLotUnit)`
- 폐기 절감: `ΔQ = max(0, baseline − Q*)` → `×ItemMaster.kgPerUnit` 로 kg 환산
- 탄소: `ΔE = Σ ΔQᵢ × (EF_prod,ᵢ + EF_waste,ᵢ)` / 보장 = `Σ ΔQᵢ×EF_waste,ᵢ`, 잠재 = 전체
- 정확도: `WAPE = Σ|actual−pred| / Σactual`

## 부록 B. 이번 개정(v2)에서 바로잡은 outdated 항목
1. **HTTP 클라이언트**: WebFlux+WebClient → **RestClient/`@HttpExchange`** (리액티브 의존성 제거).
2. **Spring Boot**: 3.5.3 → **3.5.13**(최신 패치), 4.0.x 이행 옵션 명시.
3. **springdoc-openapi**: 2.6.0 → **2.8.17**(Spring Boot 3.5 호환).
4. **PostgreSQL**: 16 → **17**.
5. 응답 형식: **`{success,data}` / `{success,error{code,message}}`** envelope으로 일관화(`ApiResponse<T>`). 상세 API 계약은 `backend_api_spec.md`.
6. Actuator/Micrometer 메트릭·resilience4j·API versioning 등 현행 권장 패턴 반영.

## 부록 C. 다일(커버기간) 발주 산출 예시 — 우유, 발주주기 7일·리드타임 1일
요청→예측응답→식 산출→프론트 응답까지 한 품목 전 과정 예시.

**C.0 전제 (마스터·정책·비용)**
| 구분 | 값 |
|---|---|
| ItemMaster | 우유, unit=L, kgPerUnit=1.03, efProd=3.0, efWaste=0.2, 원가 1,000원/L, 판매단가 1,800원/L, wasteTarget=true |
| OrderPolicy | 리드타임 1일, 발주주기 7일 → **커버기간 H=8일**, 발주단위 lot=2L |
| 비용 | `Cu = 1800−1000 = 800`, `Co = 1000+폐기처리비100 = 1100` |
| 현재고 | 최신 기말재고 = 12 L |

**C.1 예측 요청** — `POST {ai.base-url}/v1/order-recommendation`
```json
{ "storeId":1, "targetDate":"2026-06-27",
  "coverage":{ "leadTimeDays":1, "orderCycleDays":7, "coverageDays":8 },
  "weather":[ {"forecastDate":"2026-06-28","avgTemp":21.2,"precipitationProb":20,"skyCode":1},
              {"forecastDate":"2026-07-01","avgTemp":19.5,"precipitationProb":80,"skyCode":4} ],
  "rows":[{ "itemId":101,"orderCycleDays":7,"leadTimeDays":1,
            "features":{"dayOfWeek":0,"isHoliday":false,"ma7":9.4,"trend":0.1} }] }
```

**C.2 예측 응답 (AI → Spring)** — 커버기간 8일 일별 분위
```json
{ "modelVersion":"lgbm_global_v1",
  "predictions":[{ "itemId":101, "daily":[
    {"date":"2026-06-28","p10":6,"p50":8,"p90":11},
    {"date":"2026-06-29","p10":7,"p50":9,"p90":12},
    {"date":"2026-06-30","p10":7,"p50":9,"p90":12},
    {"date":"2026-07-01","p10":6,"p50":8,"p90":11},
    {"date":"2026-07-02","p10":7,"p50":9,"p90":12},
    {"date":"2026-07-03","p10":9,"p50":12,"p90":16},
    {"date":"2026-07-04","p10":10,"p50":14,"p90":19},
    {"date":"2026-07-05","p10":8,"p50":11,"p90":15} ]}]}
```

**C.3 백엔드 결정적 산출**
```
① 커버기간 합산:  P10ₕ=60,  P50ₕ=80,  P90ₕ=108        # 8일치 Σ
② 임계분위:       CR = 800/(800+1100) = 0.421
③ 목표재고 Q*:    CR∈(0.10,0.50] → (P10ₕ,P50ₕ) 보간
                  fraction=(0.421−0.10)/0.40=0.803
                  Q* = 60 + 0.803×(80−60) = 76.05 L
④ 실발주:         max(0, 76.05−12)=64.05 → lot=2 올림 → 66 L
⑤ 폐기 회피 ΔQ:   baseline(동요일평균11×8)=88,  ΔQ=88−76.05=11.95 L
                  ΔQ_kg = 11.95×1.03 = 12.31 kg
⑥ 탄소:           보장 = 12.31×0.2  = 2.46 kgCO₂eq
                  잠재 = 12.31×3.2  = 39.4 kgCO₂eq
                  폐기비용 회피 = 11.95×1000 ≈ 11,950원,  승용차 ≈ 39.4/4.6 ≈ 8.6 km
```

**C.4 최종 프론트엔드 응답**
```json
// GET /api/v1/recommendations?storeId=1&date=2026-06-27
{ "items":[{ "itemId":101,"itemName":"우유","unit":"L",
  "coverage":{"leadTimeDays":1,"orderCycleDays":7,"coverageDays":8},
  "horizonForecast":{"p10":60,"p50":80,"p90":108},
  "recommendedQuantity":66,"optimalStockQuantity":76.1,"onHand":12,
  "baselineQuantity":88,"criticalRatio":0.421,"expectedWasteAvoidedKg":12.31,
  "rationale":{"cu":800,"co":1100,"lotUnit":2,"interpolation":"P10h~P50h @0.803"} }]}

// GET /api/v1/carbon/today?storeId=1
{ "storeId":1,"targetDate":"2026-06-27",
  "guaranteedSavingKg":2.46,"potentialSavingKg":39.4,
  "wasteCostAvoidedKrw":11950,"carEquivalentKm":8.6,
  "byItem":[{"itemId":101,"itemName":"우유","wasteAvoidedKg":12.31,
             "guaranteedSavingKg":2.46,"potentialSavingKg":39.4}] }

// POST /api/v1/chat  (sLLM은 위 수치를 문장으로만 전달, 생성 안 함)
{ "answer":"다음 발주주기(7일)에 우유는 66L 발주를 권장합니다. 8일 커버 수요 중앙값은 80L이나 폐기비용이 마진보다 커서 보수적으로 목표재고 76L−현재고 12L로 잡았습니다. 평소 관행(88L) 대비 약 12L 과발주를 줄여 폐기 12.3kg·탄소 약 39kgCO₂eq(승용차 8.6km)를 절감합니다.",
  "groundedOn":{"recommendationId":456,"carbonSavingId":789},
  "cacheHit":false,"llmLatencyMs":180,"tokens":142 }
```

**흐름:** `AI 8일 일별분위 ─Σ→ 60/80/108 ─CR0.421 보간→ Q*76 ─(−12, lot올림)→ 발주 66L ─(baseline88−Q*)→ ΔQ12L ─×배출계수→ 탄소 보장2.5/잠재39kg`
