# Zero-Waste Copilot 백엔드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** POS·날씨·발주정책으로 발주 도래 품목의 커버기간 최적 발주량과 절감 탄소를 산출하는 Spring Boot 백엔드를 구축한다(수요예측·sLLM은 외부 Python AI 서버 호출).

**Architecture:** 모듈러 모놀리식 + 얇은 포트. 도메인별 `controller/dto/domain/service/repository`, 외부 경계(weather·forecast·chat)만 `port` 인터페이스 + 어댑터. 결정적 산정(newsvendor·탄소)은 Spring 내부, ML 추론·자연어는 AI 서버(`ForecastPort`/`LlmPort`)에 위임. PostgreSQL + Flyway, S3 월말 아카이빙.

**Tech Stack:** Java 21, Spring Boot 3.5.13(Web·Data JPA·Validation·Actuator·Security), Flyway, PostgreSQL 17(H2 테스트), RestClient + `@HttpExchange`, resilience4j 2.2.0, springdoc 2.8.17, AWS SDK v2(S3), JUnit5.

## Global Constraints

- Spring Boot **3.5.13**, Java **21**, Gradle wrapper 8.14.5. (현 `build.gradle`는 3.5.3 → 업)
- 외부 HTTP는 **RestClient/`@HttpExchange`만**. `spring-boot-starter-webflux` 추가 금지.
- springdoc-openapi-starter-webmvc-ui **2.8.17**, resilience4j-spring-boot3 **2.2.0**, AWS SDK BOM **2.31.0**.
- DB 스키마는 **Flyway가 소유**(`spring.jpa.hibernate.ddl-auto: validate`). 모든 결과 테이블 `(store_id, *_date)` upsert 멱등.
- **개발 수준 = Demo**: 단일 매장 가정, 합성 CSV, 수동 트리거 우선. 결정적 산정식·계측 정확성은 비타협.
- 시간대 KST(`Asia/Seoul`), 날짜 `YYYY-MM-DD`. 통화 원(KRW).
- 모든 금액/수량은 `BigDecimal`(또는 명세상 `double` 허용된 산정식 내부 계산). 엔티티 수량은 `numeric`.
- AI 계약: `POST /v1/order-recommendation`(발주용 커버기간), `POST /v1/forecast`(익일), `POST /v1/generate`(sLLM). 날씨 기온은 **avgTemp**(평균온도)만 전달. 예측 요청에 판매 CSV **presigned URL** 첨부.
- git 커밋 메시지에 **`Co-Authored-By` 트레일러 금지**.
- 참조 스펙: `docs/backend_spec.md`, `docs/ai_server_api_spec.md`, `docs/superpowers/specs/2026-06-26-zerowave-backend-design.md`.

---

## 파일 구조 (생성/수정 맵)

```
build.gradle                                  (수정) 의존성·플러그인 버전
.env.example                                  (생성) 민감 환경변수 템플릿(.env는 gitignore)
src/main/resources/application.yml            (생성) 설정(§6 backend_spec)
src/main/resources/db/migration/
  V1__schema.sql                              (생성) 전체 DDL
  V2__seed_item_master.sql                    (생성) 품목·EF·단가 시드
  V3__seed_store_demo.sql                     (생성) 데모 매장
  V4__seed_order_policy.sql                   (생성) 발주정책 시드
src/main/java/com/netzero/
  common/         BaseEntity, error/ApiException·ErrorCode·GlobalExceptionHandler, web/CsvWriter
  config/         SecurityConfig, WebConfig(CORS), OpenApiConfig, HttpClientConfig, SchedulingConfig, S3Config, AppProperties
  store/          domain/{Store,ItemMaster,OrderPolicy,ItemCategory,StorageCondition}, repository/*, dto/*
  ingest/         controller/IngestController, service/{SalesCsvService,InventoryCsvService}, dto/IngestResult, CsvParser
  weather/        domain/WeatherForecast, dto/WeatherSnapshot, port/{KmaForecastPort,KmaForecastClient}, service/WeatherService, repository/*
  feature/        FeatureBuilder, HolidayCalendar, dto/FeatureVector
  forecast/       domain/DemandForecast, dto/{ForecastRequest,ForecastResponse,DailyQuantile}, port/{ForecastPort,AiForecastClient}, service/DemandForecastService, repository/*
  order/          domain/OrderRecommendation, service/{OrderOptimizationService,Newsvendor,QuantileInterpolator,DueItemSelector}, controller/RecommendationController, dto/*, repository/*
  carbon/         domain/CarbonSaving, service/CarbonAccountingService, controller/CarbonController, dto/*, repository/*
  export/         service/{SalesCsvExporter,InventoryFlowExporter,S3ArchiveService,PresignService}, scheduler/MonthlyExportScheduler, controller/ExportController
  pipeline/       service/DailyPipelineService, scheduler/PipelineScheduler, controller/PipelineController, dto/PipelineResult
  chat/           port/{LlmPort,AiLlmClient}, service/{ChatService,RagContextAssembler}, controller/ChatController, dto/*
  dashboard/      service/DashboardService, controller/DashboardController, dto/*
  metrics/        ForecastMetrics (Micrometer 래퍼)
src/test/java/com/netzero/...                 (각 도메인 테스트)
```

규칙: 한 파일 한 책임. 알고리즘(QuantileInterpolator/Newsvendor/CarbonAccountingService/DueItemSelector)은 **순수 함수**로 분리해 단위테스트 100%.

---

## Phase M0 — 빌드/부팅 골격

### Task 0.1: 의존성·설정 갱신과 부팅 확인

**Files:**
- Modify: `build.gradle`, `.gitignore`
- Create: `src/main/resources/application.yml`, `src/test/resources/application.yml`, `.env.example`
- Delete: `src/main/resources/application.properties` (yml로 대체)
- Test: `src/test/java/com/netzero/NetzeroApplicationTests.java` (기존)

**Interfaces:**
- Produces: 부팅 가능한 Spring Boot 3.5.13 앱(웹·JPA·validation·actuator·flyway·s3·spring-dotenv 의존성 포함). `.env`로 민감정보 주입(`.env.example` 템플릿 제공).

- [ ] **Step 1: build.gradle 교체**

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.13'
    id 'io.spring.dependency-management' version '1.1.7'
}
group = 'com.netzero'
version = '0.0.1-SNAPSHOT'
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly  'org.postgresql:postgresql'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
    implementation platform('software.amazon.awssdk:bom:2.31.0')
    implementation 'software.amazon.awssdk:s3'
    implementation 'me.paulschwarz:spring-dotenv:4.0.0'   // .env → ${VAR} 자동 주입
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
tasks.named('test') { useJUnitPlatform() }
```

- [ ] **Step 2: application.yml 작성**

```yaml
spring:
  application.name: zerowave
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/zerowave}
    username: ${DB_USER:zerowave}
    password: ${DB_PASSWORD:zerowave}
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
    properties.hibernate.jdbc.time_zone: Asia/Seoul
  flyway.enabled: true
external:
  kma:
    base-url: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0
    service-key: ${KMA_SERVICE_KEY:}
ai:
  base-url: ${AI_SERVER_URL:http://localhost:8000}
  order-recommendation-path: /v1/order-recommendation
  forecast-path: /v1/forecast
  generate-path: /v1/generate
  sales-history-months: 3
storage:
  s3:
    bucket: ${S3_BUCKET:zerowave}
    region: ${AWS_REGION:ap-northeast-2}
    endpoint: ${S3_ENDPOINT:}
    presign-expiry-seconds: 600
security:
  api-key: ${SECURITY_API_KEY:dev-demo-key}
optimization: { default-cu: 1.0, default-co: 1.0 }
carbon: { car-kgco2-per-km: 4.6 }
management.endpoints.web.exposure.include: health,info,metrics,prometheus
```

> AWS 자격증명(`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`)은 application.yml에 두지 않고 AWS SDK 기본 체인(환경변수/.env)으로 주입한다.

For tests, add `src/test/resources/application.yml` overriding datasource to H2 and `spring.jpa.hibernate.ddl-auto: validate` with Flyway on H2 (`spring.flyway.url` inherits). Use H2 Postgres mode:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:zerowave;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa.database-platform: org.hibernate.dialect.H2Dialect
```

- [ ] **Step 3: `.env.example` 작성 + `.gitignore`에 `.env` 추가**

민감정보는 `.env`로 관리하고 커밋하지 않는다. `.env.example`은 키 목록만 담은 템플릿(값은 더미)으로 커밋한다. `spring-dotenv`가 부팅 시 `.env`를 읽어 application.yml의 `${VAR}`로 주입한다(실행 전 `cp .env.example .env` 후 값 채움).

`.env.example` (프로젝트 루트, 커밋함):
```dotenv
# === Database (PostgreSQL) ===
DB_URL=jdbc:postgresql://localhost:5432/zerowave
DB_USER=zerowave
DB_PASSWORD=change-me

# === Python AI 서버 (수요예측 + sLLM) ===
AI_SERVER_URL=http://localhost:8000

# === 기상청 단기예보 API ===
KMA_SERVICE_KEY=your-data-go-kr-service-key

# === AWS S3 (월말 아카이빙 + presigned URL) ===
S3_BUCKET=zerowave
AWS_REGION=ap-northeast-2
S3_ENDPOINT=                      # MinIO/LocalStack 사용 시 예: http://localhost:9000 (실 S3는 비움)
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key

# === API 보안 ===
SECURITY_API_KEY=dev-demo-key     # 쓰기 엔드포인트 X-API-Key
```

`.gitignore`에 다음 줄 추가(이미 있으면 생략):
```gitignore
.env
```

- [ ] **Step 4: application.properties 삭제**

Run: `git rm src/main/resources/application.properties`

- [ ] **Step 5: 빌드/부팅 테스트 실행**

Run: `./gradlew test`
Expected: PASS (`NetzeroApplicationTests.contextLoads` 통과). `.env` 없이도 application.yml의 `${VAR:default}` 기본값으로 부팅. Flyway가 비어있어도 OK(엔티티는 Task 1.x에서 추가).

> 주: V1 마이그레이션이 없으면 `ddl-auto: validate`가 매핑 검증할 엔티티도 없어 통과.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/resources/application.yml src/test/resources/application.yml .env.example .gitignore
git rm src/main/resources/application.properties
git commit -m "build: upgrade to Spring Boot 3.5.13, add core deps and .env.example (M0)"
```

---

## Phase M1 — 스키마·마스터·수집·추출(로컬)

### Task 1.1: Flyway V1 스키마 + 공통 BaseEntity·enum

**Files:**
- Create: `src/main/resources/db/migration/V1__schema.sql`
- Create: `src/main/java/com/netzero/common/BaseEntity.java`
- Create: `src/main/java/com/netzero/store/domain/ItemCategory.java`, `StorageCondition.java`
- Test: `src/test/java/com/netzero/migration/FlywayMigrationTest.java`

**Interfaces:**
- Produces: 테이블 `store, item_master, order_policy, sales_record, inventory_snapshot, weather_forecast, demand_forecast, order_recommendation, carbon_saving`. enum 값 `ItemCategory{완제품,원재료,판매음료,소모품}`, `StorageCondition{상온,냉장,냉동}`.

- [ ] **Step 1: V1__schema.sql 작성** (backend_spec §3.2 필드 그대로)

```sql
CREATE TABLE store (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL,
  region VARCHAR(40), nx INT, ny INT, created_at TIMESTAMPTZ DEFAULT now());

CREATE TABLE item_master (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(80) NOT NULL,
  category VARCHAR(12) NOT NULL CHECK (category IN ('완제품','원재료','판매음료','소모품')),
  waste_target BOOLEAN NOT NULL DEFAULT TRUE, unit VARCHAR(12) NOT NULL,
  shelf_life_days INT, storage_condition VARCHAR(6) CHECK (storage_condition IN ('상온','냉장','냉동')),
  kg_per_unit NUMERIC(10,4), ef_prod NUMERIC(8,3), ef_waste NUMERIC(8,3),
  purchase_price NUMERIC(12,2), price_unit VARCHAR(12), note TEXT);

CREATE TABLE order_policy (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), item_name VARCHAR(80),
  category VARCHAR(12), order_method VARCHAR(16), order_cycle_days INT NOT NULL,
  lead_time_days INT NOT NULL, safety_z NUMERIC(5,2), order_lot_unit NUMERIC(12,3) DEFAULT 1, note TEXT,
  UNIQUE (store_id, item_id));

CREATE TABLE sales_record (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  business_date DATE NOT NULL, day_of_week VARCHAR(3), weather VARCHAR(4) CHECK (weather IN ('맑음','흐림','비')),
  avg_temp NUMERIC(4,1), precipitation_mm NUMERIC(6,2), event VARCHAR(40), new_menu VARCHAR(40),
  category VARCHAR(12), item_id BIGINT NOT NULL REFERENCES item_master(id),
  quantity_sold NUMERIC(12,3) NOT NULL, scenario_note TEXT,
  UNIQUE (store_id, item_id, business_date));
CREATE INDEX ix_sales_store_date ON sales_record(store_id, business_date);

CREATE TABLE inventory_snapshot (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  business_date DATE NOT NULL, day_of_week VARCHAR(3),
  item_id BIGINT NOT NULL REFERENCES item_master(id), category VARCHAR(12), unit VARCHAR(12),
  ordered_qty NUMERIC(12,3) DEFAULT 0, opening_stock NUMERIC(12,3), demand NUMERIC(12,3),
  actual_sales NUMERIC(12,3), stockout NUMERIC(12,3), waste_qty NUMERIC(12,3), closing_stock NUMERIC(12,3),
  waste_kg NUMERIC(12,3), waste_carbon_kg NUMERIC(12,3), waste_cost_krw NUMERIC(14,2), last_order_date DATE,
  UNIQUE (store_id, item_id, business_date));
CREATE INDEX ix_inv_store_item_date ON inventory_snapshot(store_id, item_id, business_date);

CREATE TABLE weather_forecast (
  id BIGSERIAL PRIMARY KEY, region VARCHAR(40), forecast_date DATE NOT NULL,
  temp_max NUMERIC(4,1), temp_min NUMERIC(4,1), avg_temp NUMERIC(4,1),
  precipitation_mm NUMERIC(6,2), precipitation_prob INT, sky_code INT, fetched_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (region, forecast_date, fetched_at));

CREATE TABLE demand_forecast (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  predicted_quantity NUMERIC(12,3), p10 NUMERIC(12,3), p50 NUMERIC(12,3), p90 NUMERIC(12,3),
  model_version VARCHAR(40), features JSONB, UNIQUE (store_id, item_id, target_date));

CREATE TABLE order_recommendation (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  recommended_quantity NUMERIC(12,3), optimal_stock_quantity NUMERIC(12,3), baseline_quantity NUMERIC(12,3),
  critical_ratio NUMERIC(6,4), expected_waste_avoided_kg NUMERIC(12,3), rationale JSONB,
  UNIQUE (store_id, item_id, target_date));

CREATE TABLE carbon_saving (
  id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id),
  item_id BIGINT NOT NULL REFERENCES item_master(id), target_date DATE NOT NULL,
  waste_avoided_kg NUMERIC(12,3), guaranteed_saving_kg NUMERIC(12,3), potential_saving_kg NUMERIC(12,3),
  ef_prod_snapshot NUMERIC(8,3), ef_waste_snapshot NUMERIC(8,3), UNIQUE (store_id, item_id, target_date));
```

- [ ] **Step 2: enum + BaseEntity 작성**

```java
// store/domain/ItemCategory.java
package com.netzero.store.domain;
public enum ItemCategory { 완제품, 원재료, 판매음료, 소모품 }
```
```java
// store/domain/StorageCondition.java
package com.netzero.store.domain;
public enum StorageCondition { 상온, 냉장, 냉동 }
```
```java
// common/BaseEntity.java
package com.netzero.common;
import jakarta.persistence.*;
@MappedSuperclass
public abstract class BaseEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  public Long getId() { return id; }
}
```

- [ ] **Step 3: Flyway 마이그레이션 테스트 작성**

```java
// test/.../migration/FlywayMigrationTest.java
package com.netzero.migration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class FlywayMigrationTest {
  @Autowired JdbcTemplate jdbc;
  @Test void allTablesCreated() {
    Integer n = jdbc.queryForObject(
      "select count(*) from information_schema.tables where table_name in " +
      "('store','item_master','order_policy','sales_record','inventory_snapshot'," +
      "'weather_forecast','demand_forecast','order_recommendation','carbon_saving')", Integer.class);
    assertThat(n).isEqualTo(9);
  }
}
```

- [ ] **Step 4: 실행하여 통과 확인**

Run: `./gradlew test --tests com.netzero.migration.FlywayMigrationTest`
Expected: PASS (H2 PostgreSQL 모드에서 Flyway가 V1 적용, 9개 테이블 확인).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V1__schema.sql src/main/java/com/netzero/common src/main/java/com/netzero/store/domain src/test/java/com/netzero/migration
git commit -m "feat: add V1 schema and base entities (M1)"
```

### Task 1.2: 마스터 엔티티 + 시드(V2~V4) + 리포지토리

**Files:**
- Create: `store/domain/{Store,ItemMaster,OrderPolicy}.java`, `store/repository/{StoreRepository,ItemMasterRepository,OrderPolicyRepository}.java`
- Create: `db/migration/V2__seed_item_master.sql`, `V3__seed_store_demo.sql`, `V4__seed_order_policy.sql`
- Test: `src/test/java/com/netzero/store/ItemMasterRepositoryTest.java`

**Interfaces:**
- Produces:
  - `ItemMaster` getters: `getId():Long, getName():String, getCategory():ItemCategory, isWasteTarget():boolean, getUnit():String, getShelfLifeDays():Integer, getKgPerUnit():BigDecimal, getEfProd():BigDecimal, getEfWaste():BigDecimal, getPurchasePrice():BigDecimal`.
  - `OrderPolicy` getters: `getItemId():Long, getOrderCycleDays():int, getLeadTimeDays():int, getOrderLotUnit():BigDecimal, getSafetyZ():BigDecimal`.
  - `ItemMasterRepository extends JpaRepository<ItemMaster,Long>` + `Optional<ItemMaster> findByName(String)`.
  - `OrderPolicyRepository` + `List<OrderPolicy> findByStoreId(Long)`.

- [ ] **Step 1: 엔티티 작성** (필드 = V1 컬럼, `@Entity @Table(name=...)`, enum은 `@Enumerated(EnumType.STRING)`)

```java
// store/domain/ItemMaster.java
package com.netzero.store.domain;
import com.netzero.common.BaseEntity; import jakarta.persistence.*; import java.math.BigDecimal;
@Entity @Table(name="item_master")
public class ItemMaster extends BaseEntity {
  private String name;
  @Enumerated(EnumType.STRING) private ItemCategory category;
  @Column(name="waste_target") private boolean wasteTarget;
  private String unit;
  @Column(name="shelf_life_days") private Integer shelfLifeDays;
  @Enumerated(EnumType.STRING) @Column(name="storage_condition") private StorageCondition storageCondition;
  @Column(name="kg_per_unit") private BigDecimal kgPerUnit;
  @Column(name="ef_prod") private BigDecimal efProd;
  @Column(name="ef_waste") private BigDecimal efWaste;
  @Column(name="purchase_price") private BigDecimal purchasePrice;
  @Column(name="price_unit") private String priceUnit;
  private String note;
  protected ItemMaster() {}
  public String getName(){return name;} public ItemCategory getCategory(){return category;}
  public boolean isWasteTarget(){return wasteTarget;} public String getUnit(){return unit;}
  public Integer getShelfLifeDays(){return shelfLifeDays;} public BigDecimal getKgPerUnit(){return kgPerUnit;}
  public BigDecimal getEfProd(){return efProd;} public BigDecimal getEfWaste(){return efWaste;}
  public BigDecimal getPurchasePrice(){return purchasePrice;}
}
```
(`Store`, `OrderPolicy`도 동일 패턴으로 V1 컬럼 매핑.)

- [ ] **Step 2: 시드 마이그레이션 작성** (대표 품목 6~8개; 값은 backend_spec EF 예시)

```sql
-- V2__seed_item_master.sql
INSERT INTO item_master (name,category,waste_target,unit,shelf_life_days,storage_condition,kg_per_unit,ef_prod,ef_waste,purchase_price,price_unit,note) VALUES
 ('우유','원재료',true,'L',7,'냉장',1.03,3.0,0.2,1000,'원/L','EF: OWID/Poore2018 우유≈3'),
 ('치즈','원재료',true,'kg',30,'냉장',1.0,21.0,0.2,9000,'원/kg','치즈≈21'),
 ('베이커리','완제품',true,'ea',2,'상온',0.08,1.1,0.2,1500,'원/ea','빵류'),
 ('아메리카노','판매음료',false,'ea',1,'상온',0,0,0,500,'원/ea','소모 원두 별도'),
 ('원두','원재료',true,'kg',180,'상온',1.0,17.0,0.2,25000,'원/kg','커피 생두/원두'),
 ('컵','소모품',false,'ea',3650,'상온',0,0,0,40,'원/ea','폐기대상 아님');
```
```sql
-- V3__seed_store_demo.sql
INSERT INTO store (name,region,nx,ny) VALUES ('데모카페 강남점','서울_강남',61,125);
```
```sql
-- V4__seed_order_policy.sql  (store_id=1 가정, 단일 매장)
INSERT INTO order_policy (store_id,item_id,item_name,category,order_method,order_cycle_days,lead_time_days,safety_z,order_lot_unit,note)
SELECT 1, im.id, im.name, im.category, '신문팔이',
  CASE WHEN im.name='원두' THEN 14 ELSE 7 END, 1, 1.0,
  CASE WHEN im.unit='ea' THEN 1 ELSE 2 END, NULL
FROM item_master im WHERE im.category <> '소모품';
```

- [ ] **Step 3: 리포지토리 + 테스트 작성**

```java
// test/.../store/ItemMasterRepositoryTest.java
@org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
class ItemMasterRepositoryTest {
  @org.springframework.beans.factory.annotation.Autowired ItemMasterRepository repo;
  @org.junit.jupiter.api.Test void seedLoadedAndFindByName() {
    var milk = repo.findByName("우유").orElseThrow();
    org.assertj.core.api.Assertions.assertThat(milk.getEfProd()).isEqualByComparingTo("3.0");
    org.assertj.core.api.Assertions.assertThat(milk.isWasteTarget()).isTrue();
  }
}
```
> `@DataJpaTest`는 Flyway 시드를 적용하므로 V2 데이터가 로드됨.

- [ ] **Step 4: 실행**

Run: `./gradlew test --tests com.netzero.store.ItemMasterRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/netzero/store src/main/resources/db/migration/V2__seed_item_master.sql src/main/resources/db/migration/V3__seed_store_demo.sql src/main/resources/db/migration/V4__seed_order_policy.sql src/test/java/com/netzero/store
git commit -m "feat: add master entities, seeds, repositories (M1)"
```

### Task 1.3: 공통 응답 Envelope + 에러 처리

**Files:**
- Create: `common/ApiResponse.java`, `common/error/{ErrorCode,ApiException,GlobalExceptionHandler}.java`
- Test: `src/test/java/com/netzero/common/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `ApiResponse<T>` — `record ApiResponse<T>(boolean success, T data, ApiError error)` with `static <T> ApiResponse<T> ok(T data)` (success=true, error=null) and `static ApiResponse<?> error(ErrorCode code, String message)`. `record ApiError(String code, String message)`. **null 필드는 JSON에서 생략**(`@JsonInclude(NON_NULL)`).
  - `enum ErrorCode` (각 `HttpStatus status` + `String defaultMessage`): `VALIDATION_ERROR, INVALID_CSV, ITEM_NOT_FOUND, STORE_NOT_FOUND, CONTENT_NOT_FOUND, FORECAST_UNAVAILABLE, WEATHER_FETCH_FAILED, LLM_UNAVAILABLE, PIPELINE_ALREADY_RUNNING, INTERNAL_ERROR`.
  - `ApiException(ErrorCode code, String message)`.
  - `@RestControllerAdvice` → `ResponseEntity<ApiResponse<?>>` with HTTP status = `code.status`, body = `{success:false, error:{code,message}}`.
- 컨트롤러는 성공 시 `ApiResponse.ok(payload)` 반환(backend_api_spec §1.1).

- [ ] **Step 1: ApiResponse + ErrorCode + ApiException 작성**

```java
// common/ApiResponse.java
package com.netzero.common;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.netzero.common.error.ErrorCode;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {
  public record ApiError(String code, String message) {}
  public static <T> ApiResponse<T> ok(T data){ return new ApiResponse<>(true, data, null); }
  public static ApiResponse<Void> error(ErrorCode code, String message){
    return new ApiResponse<>(false, null, new ApiError(code.name(), message));
  }
}
```
```java
// common/error/ErrorCode.java
package com.netzero.common.error;
import org.springframework.http.HttpStatus;
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
  INVALID_CSV(HttpStatus.BAD_REQUEST, "CSV 형식이 올바르지 않습니다."),
  ITEM_NOT_FOUND(HttpStatus.BAD_REQUEST, "품목을 찾을 수 없습니다."),
  STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
  FORECAST_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "수요예측 서버를 사용할 수 없습니다."),
  WEATHER_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "날씨 정보를 가져오지 못했습니다."),
  LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챗봇 서버를 사용할 수 없습니다."),
  PIPELINE_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 실행 중인 파이프라인이 있습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 오류가 발생했습니다.");
  public final HttpStatus status; public final String defaultMessage;
  ErrorCode(HttpStatus s, String m){ this.status=s; this.defaultMessage=m; }
}
```
```java
// common/error/ApiException.java
package com.netzero.common.error;
public class ApiException extends RuntimeException {
  public final ErrorCode code;
  public ApiException(ErrorCode code){ super(code.defaultMessage); this.code=code; }
  public ApiException(ErrorCode code, String message){ super(message); this.code=code; }
}
```

- [ ] **Step 2: GlobalExceptionHandler 작성**

```java
// common/error/GlobalExceptionHandler.java
package com.netzero.common.error;
import com.netzero.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handle(ApiException ex) {
    return ResponseEntity.status(ex.code.status)
      .body(ApiResponse.error(ex.code, ex.getMessage()));
  }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status)
      .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage));
  }
}
```

- [ ] **Step 3: 테스트 작성** (standalone MockMvc, 더미 컨트롤러로 예외 검증)

```java
// test/.../common/GlobalExceptionHandlerTest.java
package com.netzero.common;
import com.netzero.common.error.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
  @RestController static class Dummy {
    @GetMapping("/boom") String boom(){ throw new ApiException(ErrorCode.CONTENT_NOT_FOUND, "파일을 찾을 수 없습니다."); }
  }
  @Test void returnsEnvelopeError() throws Exception {
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new Dummy())
      .setControllerAdvice(new GlobalExceptionHandler()).build();
    mvc.perform(get("/boom"))
      .andExpect(status().isNotFound())
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"))
      .andExpect(jsonPath("$.error.message").value("파일을 찾을 수 없습니다."))
      .andExpect(jsonPath("$.data").doesNotExist());
  }
}
```

- [ ] **Step 4: 실행** — Run: `./gradlew test --tests com.netzero.common.GlobalExceptionHandlerTest` → Expected: PASS.
- [ ] **Step 5: Commit** — `git add src/main/java/com/netzero/common src/test/java/com/netzero/common && git commit -m "feat: ApiResponse envelope and error handling (M1)"`

> 이후 모든 컨트롤러는 `ApiResponse<T>`를 반환한다(성공 `ApiResponse.ok(payload)`). 이전 태스크의 예시 응답 JSON은 `data` payload 기준이므로 컨트롤러에서 `ok()`로 감싼다.

### Task 1.4: 판매 CSV 수집 (`POST /api/v1/ingest/sales` + `/ingest/sales/daily`)

**Files:**
- Create: `store/domain/SalesRecord.java`, `store/repository/SalesRecordRepository.java`
- Create: `ingest/CsvParser.java`, `ingest/service/SalesCsvService.java`, `ingest/dto/{IngestResult,DailyIngestResult}.java`, `ingest/controller/IngestController.java`
- Create: `weather/WeatherProvider.java`, `weather/NoOpWeatherProvider.java`, `weather/dto/DailyWeather.java`
- Test: `src/test/java/com/netzero/ingest/SalesCsvServiceTest.java`

**Interfaces:**
- Consumes: `ItemMasterRepository.findByName`, `WeatherProvider`.
- Produces:
  - `record IngestResult(int accepted, int rejected, List<RowError> errors)`, `record RowError(int line, String code, String value)`.
  - `record DailyIngestResult(LocalDate appliedDate, int accepted, int rejected, boolean eventFlag, boolean newMenuFlag, List<RowError> errors)`.
  - `record DailyWeather(String weather, BigDecimal avgTemp, BigDecimal precipitationMm)`.
  - `interface WeatherProvider { Optional<DailyWeather> lookup(Long storeId, LocalDate date); }` — 기상청 날씨 조회 시임. M1엔 `NoOpWeatherProvider`(항상 `Optional.empty()`, `@ConditionalOnMissingBean` 기본 빈). M3에서 `KmaWeatherProvider`가 실연동(Task 3.2).
  - `SalesCsvService.ingest(Long storeId, InputStream csv): IngestResult` — 풀컬럼 CSV(헤더 `날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오`).
  - `SalesCsvService.ingestDaily(Long storeId, InputStream csv, boolean eventFlag, boolean newMenuFlag, String scenarioNote): DailyIngestResult` — 하루치 CSV(헤더 `날짜,요일,품목,구분,판매수량`), body 메타를 모든 행에 적용. event/newMenu는 `"Y"`/null 마커로 저장. **날씨는 `WeatherProvider.lookup(storeId,날짜)`로 보강**(M1 NoOp이면 null, M3 KMA면 채움).
  - 컨트롤러는 `ApiResponse<IngestResult>` / `ApiResponse<DailyIngestResult>` 반환.

- [ ] **Step 1: SalesRecord 엔티티 + repo + WeatherProvider 시임 작성** (SalesRecord: V1 컬럼 매핑, `getQuantitySold():BigDecimal`, `getBusinessDate():LocalDate`, `getItemId():Long`; repo: `List<SalesRecord> findByStoreIdAndBusinessDateBetween(Long, LocalDate, LocalDate)`).

```java
// weather/dto/DailyWeather.java
package com.netzero.weather.dto;
import java.math.BigDecimal;
public record DailyWeather(String weather, BigDecimal avgTemp, BigDecimal precipitationMm) {}
```
```java
// weather/WeatherProvider.java
package com.netzero.weather;
import com.netzero.weather.dto.DailyWeather;
import java.time.LocalDate; import java.util.Optional;
public interface WeatherProvider { Optional<DailyWeather> lookup(Long storeId, LocalDate date); }
```
```java
// weather/NoOpWeatherProvider.java — M1 기본 빈(KMA 미연동 시)
package com.netzero.weather;
import com.netzero.weather.dto.DailyWeather;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import java.time.LocalDate; import java.util.Optional;
@Configuration
class NoOpWeatherProvider {
  @Bean @ConditionalOnMissingBean(WeatherProvider.class)
  WeatherProvider noOpWeatherProvider() { return (storeId, date) -> Optional.empty(); }
}
```

- [ ] **Step 2: 실패 테스트 작성**

```java
// test/.../ingest/SalesCsvServiceTest.java
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.transaction.annotation.Transactional
class SalesCsvServiceTest {
  @org.springframework.beans.factory.annotation.Autowired SalesCsvService svc;
  @org.junit.jupiter.api.Test void parsesValidRowsAndRejectsUnknownItem() {
    String csv = "날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오\n" +
                 "2026-06-01,월,맑음,20.5,0,,,우유,원재료,9,평범한날\n" +
                 "2026-06-01,월,맑음,20.5,0,,,없는품목,원재료,3,오류행\n";
    var r = svc.ingest(1L, new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    org.assertj.core.api.Assertions.assertThat(r.accepted()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(r.rejected()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(r.errors().get(0).code()).isEqualTo("ITEM_NOT_FOUND");
  }
  @org.junit.jupiter.api.Test void dailyUploadAppliesBodyMeta() {
    String csv = "날짜,요일,품목,구분,판매수량\n2026-06-28,일,우유,원재료,11\n";
    var r = svc.ingestDaily(1L, new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
              false, true, "주말+맑음->수요 높음");
    org.assertj.core.api.Assertions.assertThat(r.accepted()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(r.appliedDate()).isEqualTo(java.time.LocalDate.parse("2026-06-28"));
    org.assertj.core.api.Assertions.assertThat(r.newMenuFlag()).isTrue();
  }
}
```

- [ ] **Step 3: 실행하여 실패 확인** — Run: `./gradlew test --tests com.netzero.ingest.SalesCsvServiceTest` → Expected: FAIL (SalesCsvService 없음/미구현).

- [ ] **Step 4: CsvParser + SalesCsvService 구현**

```java
// ingest/CsvParser.java — 헤더 매핑, 행→Map<String,String>
package com.netzero.ingest;
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
public class CsvParser {
  public static List<Map<String,String>> parse(InputStream in) {
    var rows = new ArrayList<Map<String,String>>();
    try (var br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String header = br.readLine();
      if (header == null) return rows;
      if (header.startsWith("﻿")) header = header.substring(1); // BOM 제거
      String[] cols = header.split(",", -1);
      String line;
      while ((line = br.readLine()) != null) {
        if (line.isBlank()) continue;
        String[] v = line.split(",", -1);
        var m = new LinkedHashMap<String,String>();
        for (int i=0;i<cols.length;i++) m.put(cols[i].trim(), i<v.length? v[i].trim():"");
        rows.add(m);
      }
    } catch (IOException e) { throw new UncheckedIOException(e); }
    return rows;
  }
}
```
```java
// ingest/service/SalesCsvService.java
package com.netzero.ingest.service;
import com.netzero.ingest.*; import com.netzero.ingest.dto.*;
import com.netzero.store.domain.*; import com.netzero.store.repository.*;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream; import java.math.BigDecimal; import java.time.LocalDate; import java.util.*;
@Service
public class SalesCsvService {
  private final ItemMasterRepository items; private final SalesRecordRepository sales;
  private final com.netzero.weather.WeatherProvider weather;
  public SalesCsvService(ItemMasterRepository i, SalesRecordRepository s, com.netzero.weather.WeatherProvider w){
    this.items=i; this.sales=s; this.weather=w;
  }
  @Transactional
  public IngestResult ingest(Long storeId, InputStream csv) {
    var rows = CsvParser.parse(csv); int accepted=0; var errors=new ArrayList<IngestResult.RowError>();
    int line=1;
    for (var r : rows) { line++;
      var item = items.findByName(r.get("품목")).orElse(null);
      if (item == null) { errors.add(new IngestResult.RowError(line,"ITEM_NOT_FOUND", r.get("품목"))); continue; }
      var rec = new SalesRecord(storeId, LocalDate.parse(r.get("날짜")), r.get("요일"), r.get("날씨"),
        bd(r.get("기온")), bd(r.get("강수mm")), nz(r.get("행사")), nz(r.get("신메뉴")),
        item.getCategory().name(), item.getId(), bd(r.get("판매수량")), nz(r.get("비고_시나리오")));
      sales.save(rec); accepted++;
    }
    return new IngestResult(accepted, errors.size(), errors);
  }
  @Transactional
  public DailyIngestResult ingestDaily(Long storeId, InputStream csv, boolean eventFlag,
                                       boolean newMenuFlag, String scenarioNote) {
    var rows = CsvParser.parse(csv); int accepted=0; var errors=new ArrayList<IngestResult.RowError>();
    LocalDate applied=null; int line=1;
    String eventMark = eventFlag? "Y" : null, newMenuMark = newMenuFlag? "Y" : null;
    var weatherCache = new java.util.HashMap<LocalDate, com.netzero.weather.dto.DailyWeather>();
    for (var r : rows) { line++;
      var item = items.findByName(r.get("품목")).orElse(null);
      if (item == null) { errors.add(new IngestResult.RowError(line,"ITEM_NOT_FOUND", r.get("품목"))); continue; }
      LocalDate d = LocalDate.parse(r.get("날짜")); applied = d;
      // 기상청 날씨 보강(날짜당 1회 조회, 실패/미연동 시 null)
      var w = weatherCache.computeIfAbsent(d, dd -> weather.lookup(storeId, dd).orElse(null));
      String wx = w!=null? w.weather() : null;
      BigDecimal temp = w!=null? w.avgTemp() : null, precip = w!=null? w.precipitationMm() : null;
      var rec = new SalesRecord(storeId, d, r.get("요일"), wx, temp, precip,
        eventMark, newMenuMark, item.getCategory().name(), item.getId(),
        bd(r.get("판매수량")), nz(scenarioNote));
      sales.save(rec); accepted++;
    }
    return new DailyIngestResult(applied, accepted, errors.size(), eventFlag, newMenuFlag, errors);
  }
  private static BigDecimal bd(String s){ return s==null||s.isBlank()? null : new BigDecimal(s); }
  private static String nz(String s){ return s==null||s.isBlank()? null : s; }
}
```
(`SalesRecord`에 위 인자 순서의 생성자 추가. `IngestResult`·`DailyIngestResult` record 작성.)

- [ ] **Step 5: 실행하여 통과** — Run: `./gradlew test --tests com.netzero.ingest.SalesCsvServiceTest` → Expected: PASS.

- [ ] **Step 6: IngestController 추가 + 슬라이스 테스트**

```java
// ingest/controller/IngestController.java
package com.netzero.ingest.controller;
import com.netzero.common.ApiResponse; import com.netzero.ingest.dto.*;
import com.netzero.ingest.service.SalesCsvService;
import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
@RestController @RequestMapping("/api/v1/ingest")
public class IngestController {
  private final SalesCsvService sales;
  public IngestController(SalesCsvService s){ this.sales=s; }

  @PostMapping(value="/sales", consumes="multipart/form-data")
  public ApiResponse<IngestResult> sales(@RequestParam Long storeId,
      @RequestParam MultipartFile file) throws IOException {
    return ApiResponse.ok(sales.ingest(storeId, file.getInputStream()));
  }

  @PostMapping(value="/sales/daily", consumes="multipart/form-data")
  public ApiResponse<DailyIngestResult> salesDaily(@RequestParam Long storeId,
      @RequestParam MultipartFile file,
      @RequestParam boolean eventFlag, @RequestParam boolean newMenuFlag,
      @RequestParam(required=false) String scenarioNote) throws IOException {
    return ApiResponse.ok(sales.ingestDaily(storeId, file.getInputStream(), eventFlag, newMenuFlag, scenarioNote));
  }
}
```

- [ ] **Step 7: Commit** — `git add src/main/java/com/netzero/ingest src/main/java/com/netzero/store/domain/SalesRecord.java src/main/java/com/netzero/store/repository/SalesRecordRepository.java src/test/java/com/netzero/ingest && git commit -m "feat: sales CSV ingest (bulk + daily) (M1)"`

### Task 1.5: 재고 CSV 수집 + InventorySnapshot 엔티티

**Files:**
- Create: `store/domain/InventorySnapshot.java`, `store/repository/InventorySnapshotRepository.java`
- Create: `ingest/service/InventoryCsvService.java`, controller 메서드 추가
- Test: `src/test/java/com/netzero/ingest/InventoryCsvServiceTest.java`

**Interfaces:**
- Produces: `InventorySnapshot` getters (전 컬럼). Repo: `Optional<InventorySnapshot> findTopByStoreIdAndItemIdOrderByBusinessDateDesc(Long,Long)` (최신 기말재고·lastOrderDate 조회), `findByStoreIdAndBusinessDateBetween`. `InventoryCsvService.ingest(Long,InputStream):IngestResult` (헤더 `날짜,품목,구분,단위,발주,기초재고,수요,실판매,결품,폐기,기말재고`; 산출 컬럼 폐기_kg·탄소·비용은 적재 시 ItemMaster로 계산).

- [ ] **Step 1: InventorySnapshot 엔티티+repo 작성.**
- [ ] **Step 2: 실패 테스트 작성** — CSV 1행 적재 후 `closing_stock`·`waste_kg`(폐기×kgPerUnit)·`waste_carbon_kg`(폐기kg×(efProd+efWaste))·`waste_cost_krw`(폐기×purchasePrice)·`last_order_date`(ordered_qty>0이면 businessDate) 검증.

```java
// 핵심 단언 (우유: kgPerUnit 1.03, efProd 3.0, efWaste 0.2, price 1000; 폐기 2.0, 발주 10)
// waste_kg = 2.0*1.03 = 2.06 ; waste_carbon_kg = 2.06*3.2 = 6.592 ; waste_cost_krw = 2000 ; last_order_date = 날짜
```

- [ ] **Step 3: 실행 → FAIL.**
- [ ] **Step 4: InventoryCsvService 구현** — CSV 파싱 후 품목 매칭, 산출 컬럼 계산:
```java
BigDecimal kg = wasteQty.multiply(item.getKgPerUnit());
BigDecimal carbon = item.isWasteTarget()? kg.multiply(item.getEfProd().add(item.getEfWaste())) : BigDecimal.ZERO;
BigDecimal cost = wasteQty.multiply(item.getPurchasePrice());
LocalDate lastOrder = orderedQty.signum() > 0 ? businessDate : prevLastOrderDate;
```
- [ ] **Step 5: 실행 → PASS.**
- [ ] **Step 6: Commit** — `git commit -m "feat: inventory CSV ingest with ledger computation (M1)"`

### Task 1.6: 로컬 CSV 추출 (`GET /export/sales.csv`, `/export/store-inventory.csv`)

**Files:**
- Create: `common/web/CsvWriter.java`, `export/service/{SalesCsvExporter,InventoryFlowExporter}.java`, `export/controller/ExportController.java`
- Test: `src/test/java/com/netzero/export/ExportServiceTest.java`

**Interfaces:**
- Consumes: `SalesRecordRepository`, `InventorySnapshotRepository`, `ItemMasterRepository`.
- Produces: `SalesCsvExporter.export(Long storeId, YearMonth month): String` (CSV 본문, 헤더 `날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오`). `InventoryFlowExporter.export(Long, YearMonth): String` (헤더 = InventorySnapshot 15컬럼). 컨트롤러는 `text/csv; charset=UTF-8` + BOM.

- [ ] **Step 1: 실패 테스트** — 1.4/1.5에서 적재한 6월 데이터로 `export(1L, YearMonth.of(2026,6))` 호출 → 헤더 라인 + 행 수 검증, 우유 행에 `판매수량 9` 포함.
- [ ] **Step 2: 실행 → FAIL.**
- [ ] **Step 3: CsvWriter + Exporter 구현** (월 범위 = `month.atDay(1)`~`month.atEndOfMonth()`, repo 조회 후 라인 생성, 품목명은 ItemMaster 조인).
- [ ] **Step 4: 실행 → PASS.**
- [ ] **Step 5: ExportController 추가** (`GET /api/v1/export/sales.csv?storeId=&month=YYYY-MM`, `month`는 `@RequestParam YearMonth`(ISO `2026-06`); 응답 헤더 `Content-Disposition: attachment; filename=sales-2026-06.csv`, 본문 앞 `﻿`).
- [ ] **Step 6: Commit** — `git commit -m "feat: monthly CSV exporters (M1)"`

---

## Phase M2 — 결정적 산정 코어 (TDD 집중)

### Task 2.1: QuantileInterpolator (F⁻¹ 구간선형보간)

**Files:**
- Create: `order/service/QuantileInterpolator.java`
- Test: `src/test/java/com/netzero/order/QuantileInterpolatorTest.java`

**Interfaces:**
- Produces: `static double interpolate(double p10, double p50, double p90, double cr)` — backend_spec §4.3 규칙:
  CR≤0.10→p10 외삽, 0.10<CR≤0.50→(p10,p50), 0.50<CR≤0.90→(p50,p90), CR>0.90→p90 외삽.

- [ ] **Step 1: 실패 테스트 작성**

```java
// test/.../order/QuantileInterpolatorTest.java
package com.netzero.order;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.assertThat;
import static com.netzero.order.service.QuantileInterpolator.interpolate;
class QuantileInterpolatorTest {
  @Test void midBandBetweenP10AndP50() {
    // CR=0.421 → fraction=(0.421-0.10)/0.40=0.8025 → 60+0.8025*(80-60)=76.05
    assertThat(interpolate(60,80,108,0.421)).isCloseTo(76.05, within(0.05));
  }
  @Test void upperBandBetweenP50AndP90() {
    // CR=0.70 → fraction=(0.70-0.50)/0.40=0.5 → 80+0.5*(108-80)=94
    assertThat(interpolate(60,80,108,0.70)).isCloseTo(94.0, within(0.001));
  }
  @Test void clampLowToP10() { assertThat(interpolate(60,80,108,0.05)).isEqualTo(60); }
  @Test void clampHighToP90() { assertThat(interpolate(60,80,108,0.97)).isEqualTo(108); }
  static org.assertj.core.data.Offset<Double> within(double d){ return org.assertj.core.data.Offset.offset(d); }
}
```

- [ ] **Step 2: 실행 → FAIL** (`./gradlew test --tests com.netzero.order.QuantileInterpolatorTest`).

- [ ] **Step 3: 구현**

```java
// order/service/QuantileInterpolator.java
package com.netzero.order.service;
public final class QuantileInterpolator {
  private QuantileInterpolator(){}
  public static double interpolate(double p10, double p50, double p90, double cr) {
    if (cr <= 0.10) return p10;
    if (cr <= 0.50) return p10 + (cr - 0.10)/0.40 * (p50 - p10);
    if (cr <= 0.90) return p50 + (cr - 0.50)/0.40 * (p90 - p50);
    return p90;
  }
}
```

- [ ] **Step 4: 실행 → PASS.**
- [ ] **Step 5: Commit** — `git add src/main/java/com/netzero/order/service/QuantileInterpolator.java src/test/java/com/netzero/order && git commit -m "feat: quantile interpolator (M2)"`

### Task 2.2: Newsvendor (CR + 커버기간 합산 + lot 올림)

**Files:**
- Create: `order/service/Newsvendor.java`
- Test: `src/test/java/com/netzero/order/NewsvendorTest.java`

**Interfaces:**
- Consumes: `QuantileInterpolator.interpolate`.
- Produces:
  - `record Quantiles(double p10, double p50, double p90)`.
  - `static double criticalRatio(double cu, double co)` = `cu/(cu+co)`.
  - `static Quantiles sumDaily(List<Quantiles> daily)` = 일별 합산.
  - `static double roundToLot(double qty, double lot)` = `lot<=0? qty : Math.ceil(qty/lot)*lot`.
  - `static double recommendedOrder(Quantiles horizon, double cu, double co, double onHand, double lot)` = `roundToLot(max(0, interpolate(horizon, CR) - onHand), lot)`.
  - `static double optimalStock(Quantiles horizon, double cu, double co)` = `interpolate(...)`.

- [ ] **Step 1: 실패 테스트 작성** (부록 C 수치)

```java
// test/.../order/NewsvendorTest.java
package com.netzero.order;
import com.netzero.order.service.Newsvendor; import com.netzero.order.service.Newsvendor.Quantiles;
import org.junit.jupiter.api.Test; import java.util.List;
import static org.assertj.core.api.Assertions.assertThat; import static org.assertj.core.data.Offset.offset;
class NewsvendorTest {
  @Test void criticalRatio() { assertThat(Newsvendor.criticalRatio(800,1100)).isCloseTo(0.421, offset(0.001)); }
  @Test void sumDailyAggregates() {
    var sum = Newsvendor.sumDaily(List.of(new Quantiles(6,8,11), new Quantiles(7,9,12)));
    assertThat(sum.p10()).isEqualTo(13); assertThat(sum.p50()).isEqualTo(17); assertThat(sum.p90()).isEqualTo(23);
  }
  @Test void roundUpToLot() { assertThat(Newsvendor.roundToLot(64.05, 2)).isEqualTo(66); }
  @Test void recommendedOrderMilkScenario() {
    var horizon = new Quantiles(60,80,108); // 8일 합산
    double order = Newsvendor.recommendedOrder(horizon, 800, 1100, 12, 2); // Q*=76.05, -12=64.05, lot2→66
    assertThat(order).isEqualTo(66);
  }
}
```

- [ ] **Step 2: 실행 → FAIL.**
- [ ] **Step 3: 구현**

```java
// order/service/Newsvendor.java
package com.netzero.order.service;
import java.util.List;
public final class Newsvendor {
  private Newsvendor(){}
  public record Quantiles(double p10, double p50, double p90) {}
  public static double criticalRatio(double cu, double co){ return cu/(cu+co); }
  public static Quantiles sumDaily(List<Quantiles> d){
    double a=0,b=0,c=0; for (var q: d){ a+=q.p10(); b+=q.p50(); c+=q.p90(); } return new Quantiles(a,b,c);
  }
  public static double roundToLot(double qty, double lot){ return lot<=0? qty : Math.ceil(qty/lot)*lot; }
  public static double optimalStock(Quantiles h, double cu, double co){
    return QuantileInterpolator.interpolate(h.p10(), h.p50(), h.p90(), criticalRatio(cu,co));
  }
  public static double recommendedOrder(Quantiles h, double cu, double co, double onHand, double lot){
    return roundToLot(Math.max(0, optimalStock(h,cu,co) - onHand), lot);
  }
}
```

- [ ] **Step 4: 실행 → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat: newsvendor with coverage summation (M2)"`

### Task 2.3: CarbonAccountingService (보장/잠재 분리)

**Files:**
- Create: `carbon/service/CarbonAccountingService.java`, `carbon/dto/CarbonResult.java`
- Test: `src/test/java/com/netzero/carbon/CarbonAccountingServiceTest.java`

**Interfaces:**
- Produces: `record CarbonResult(double wasteAvoidedKg, double guaranteedSavingKg, double potentialSavingKg)`.
  `CarbonResult compute(double wasteAvoidedQty, double kgPerUnit, double efProd, double efWaste, boolean wasteTarget)`:
  - `wasteAvoidedKg = wasteAvoidedQty*kgPerUnit`; non-wasteTarget이면 모두 0.
  - `guaranteed = wasteAvoidedKg*efWaste`, `potential = wasteAvoidedKg*(efProd+efWaste)`.

- [ ] **Step 1: 실패 테스트** (우유: qty 11.95, kg 1.03, efProd 3.0, efWaste 0.2 → kg=12.31, guaranteed=2.46, potential=39.4; 소모품 wasteTarget=false → 0)

```java
@Test void milk() {
  var r = svc.compute(11.95, 1.03, 3.0, 0.2, true);
  assertThat(r.wasteAvoidedKg()).isCloseTo(12.31, offset(0.01));
  assertThat(r.guaranteedSavingKg()).isCloseTo(2.46, offset(0.01));
  assertThat(r.potentialSavingKg()).isCloseTo(39.4, offset(0.1));
}
@Test void nonWasteTargetIsZero() {
  assertThat(svc.compute(5, 0, 0, 0, false).potentialSavingKg()).isZero();
}
```

- [ ] **Step 2: 실행 → FAIL.**
- [ ] **Step 3: 구현** (순수 산술, 위 식 그대로).
- [ ] **Step 4: 실행 → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat: carbon accounting service (M2)"`

### Task 2.4: ForecastPort 인터페이스 + 목 구현

**Files:**
- Create: `forecast/dto/{ForecastRequest,ForecastResponse,DailyQuantile,WeatherDay,ForecastRow,CoverageSpec}.java`, `forecast/port/ForecastPort.java`
- Create: `forecast/port/MockForecastClient.java` (`@Profile("test")` 또는 `@ConditionalOnProperty`), `weather/dto/WeatherSnapshot.java`
- Test: `src/test/java/com/netzero/forecast/MockForecastClientTest.java`

**Interfaces:**
- Produces:
  - `record WeatherSnapshot(LocalDate forecastDate, BigDecimal avgTemp, BigDecimal precipitationMm, Integer precipitationProb, Integer skyCode)`.
  - `record CoverageSpec(int leadTimeDays, int orderCycleDays, int coverageDays)`.
  - `record ForecastRow(Long itemId, int orderCycleDays, int leadTimeDays, Map<String,Object> features)`.
  - `record ForecastRequest(Long storeId, LocalDate targetDate, List<String> salesPresignedUrls, CoverageSpec coverage, List<WeatherSnapshot> weather, List<ForecastRow> rows)`.
  - `record DailyQuantile(LocalDate date, double p10, double p50, double p90)`.
  - `record ItemForecast(Long itemId, List<DailyQuantile> daily)`.
  - `record ForecastResponse(String modelVersion, List<ItemForecast> predictions)`.
  - `interface ForecastPort { ForecastResponse orderRecommendation(ForecastRequest req); }`.
  - `MockForecastClient` returns deterministic `baseline_v1` (각 일자 p50=ma7, p10=0.8·p50, p90=1.3·p50).

- [ ] **Step 1: dto/port 작성.**
- [ ] **Step 2: MockForecastClient 테스트 작성** — coverageDays=8 요청 → predictions[0].daily 길이 8, p10≤p50≤p90.
- [ ] **Step 3: 실행 → FAIL.**
- [ ] **Step 4: MockForecastClient 구현** (ma7는 features에서 읽고 위 배수 적용; date는 targetDate+1..+coverageDays).
- [ ] **Step 5: 실행 → PASS.**
- [ ] **Step 6: Commit** — `git commit -m "feat: ForecastPort + mock client (M2)"`

### Task 2.5: OrderOptimizationService + CarbonSaving 저장 + `/recommendations`·`/carbon/today`

**Files:**
- Create: `order/domain/OrderRecommendation.java`, `order/repository/*`, `order/service/OrderOptimizationService.java`, `order/controller/RecommendationController.java`, `order/dto/*`
- Create: `carbon/domain/CarbonSaving.java`, `carbon/repository/*`, `carbon/controller/CarbonController.java`
- Create: `forecast/domain/DemandForecast.java`, `forecast/repository/*`, `forecast/service/DemandForecastService.java`
- Test: `src/test/java/com/netzero/order/OrderOptimizationServiceTest.java`

**Interfaces:**
- Consumes: `Newsvendor`, `CarbonAccountingService`, `ForecastPort`, `ItemMasterRepository`, `OrderPolicyRepository`, `InventorySnapshotRepository`, `SalesRecordRepository`.
- Produces: `OrderOptimizationService.optimize(Long storeId, LocalDate targetDate, List<Long> dueItemIds): List<OrderRecommendation>` — 품목별로 ForecastPort 호출(또는 일괄), 합산, newsvendor, ΔQ·탄소 계산, OrderRecommendation·CarbonSaving·DemandForecast upsert. baseline = 과거 동요일 평균×coverageDays. cu/co는 ItemMaster(판매단가는 priceUnit 기반, 없으면 config 기본).

- [ ] **Step 1: 엔티티/리포지토리 작성** (upsert는 `findByStoreIdAndItemIdAndTargetDate` 후 갱신 or 신규).
- [ ] **Step 2: 통합 테스트 작성** — 시드 품목 우유에 대해 InventorySnapshot(현재고 12, lastOrderDate 8일 전)과 과거 판매 적재 후 `optimize(1L, date, [milkId])` → 반환 OrderRecommendation의 `recommendedQuantity`가 lot 배수, `criticalRatio` 0~1, CarbonSaving 저장 확인. (MockForecastClient 사용)

```java
@SpringBootTest @Transactional
class OrderOptimizationServiceTest {
  @Autowired OrderOptimizationService svc; @Autowired ItemMasterRepository items;
  @Autowired CarbonSavingRepository carbon; /* + 시드 적재 헬퍼 */
  @Test void producesRecommendationAndCarbon() {
    Long milk = items.findByName("우유").orElseThrow().getId();
    // given: inventory_snapshot(현재고 12), 과거 판매 일부 적재 (헬퍼)
    var recs = svc.optimize(1L, java.time.LocalDate.parse("2026-06-27"), java.util.List.of(milk));
    assertThat(recs).hasSize(1);
    assertThat(recs.get(0).getRecommendedQuantity().doubleValue() % 2).isZero(); // lot=2
    assertThat(carbon.findByStoreIdAndItemIdAndTargetDate(1L, milk,
       java.time.LocalDate.parse("2026-06-27"))).isPresent();
  }
}
```

- [ ] **Step 3: 실행 → FAIL.**
- [ ] **Step 4: DemandForecastService(ForecastPort 호출→DemandForecast 저장) + OrderOptimizationService 구현.**
  - cu/co 계산: `co = purchasePrice + 폐기처리비(config 또는 0)`, `cu = max(sellPrice − purchasePrice, default-cu)`; sellPrice 미보유 시 `default-cu`. (Demo: ItemMaster에 판매단가 없으면 config 기본 사용 — 명세 §4.3.)
  - ΔQ: `baseline − Q*`, baseline = 과거 동요일 평균 판매 × coverageDays.
- [ ] **Step 5: 실행 → PASS.**
- [ ] **Step 6: 컨트롤러 추가** — `GET /api/v1/recommendations?storeId=&date=` (저장된 OrderRecommendation 조회), `GET /api/v1/carbon/today?storeId=` (당일 CarbonSaving 합산 + byItem + carEquivalentKm = potential/`carbon.car-kgco2-per-km`). `@WebMvcTest` 슬라이스로 JSON 형태 검증.
- [ ] **Step 7: Commit** — `git commit -m "feat: order optimization + carbon + recommendations/carbon APIs (M2)"`

---

## Phase M3 — 외부연동·파이프라인·S3

### Task 3.1: DueItemSelector (발주 도래 품목 선별)

**Files:**
- Create: `order/service/DueItemSelector.java`
- Test: `src/test/java/com/netzero/order/DueItemSelectorTest.java`

**Interfaces:**
- Consumes: `OrderPolicyRepository`, `InventorySnapshotRepository`.
- Produces: `record DueItem(Long itemId, int orderCycleDays, int leadTimeDays, LocalDate lastOrderDate, long daysSinceLastOrder)`.
  `List<DueItem> selectDue(Long storeId, LocalDate date)` — `lastOrderDate==null` 이면 도래; 아니면 `date - lastOrderDate >= orderCycleDays` 이면 도래. OrderPolicy 없는 품목 제외.

- [ ] **Step 1: 실패 테스트** — 우유(주기7, 마지막발주 8일전)=도래, 원두(주기14, 3일전)=미도래.

```java
@Test void selectsOnlyDueItems() {
  // given inventory_snapshot.last_order_date: milk=date-8, beans=date-3
  var due = selector.selectDue(1L, LocalDate.parse("2026-06-27"));
  assertThat(due).extracting(DueItem::itemId).contains(milkId).doesNotContain(beansId);
}
```

- [ ] **Step 2: 실행 → FAIL.** → **Step 3: 구현** (최신 InventorySnapshot의 lastOrderDate 조회) → **Step 4: PASS** → **Step 5: Commit** `git commit -m "feat: due-item selector (M3)"`.

### Task 3.2: 기상청 연동 (`KmaForecastPort` @HttpExchange + WeatherService) + avgTemp

**Files:**
- Create: `config/HttpClientConfig.java`, `weather/port/{KmaForecastPort,KmaForecastClient}.java`, `weather/domain/WeatherForecast.java`, `weather/repository/*`, `weather/service/WeatherService.java`, `weather/KmaWeatherProvider.java`, `weather/controller/WeatherController.java`
- Test: `src/test/java/com/netzero/weather/WeatherServiceTest.java`, `src/test/java/com/netzero/weather/KmaWeatherProviderTest.java`

**Interfaces:**
- Consumes: `WeatherProvider`(Task 1.4 인터페이스), `SalesCsvService`(daily 보강 검증).
- Produces:
  - `interface KmaForecastPort { KmaResponse getVillageForecast(Map<String,String> q); }` (`@GetExchange`).
  - `WeatherService.fetchAndStore(Long storeId, LocalDate date): WeatherSnapshot` — KMA 호출→파싱→`avgTemp=(tempMax+tempMin)/2`→WeatherForecast 저장→WeatherSnapshot 반환. `WeatherService.coverageWeather(Long storeId, LocalDate start, int days): List<WeatherSnapshot>`.
  - `KmaWeatherProvider implements WeatherProvider` (`@Primary`) — `fetchAndStore` 결과를 `DailyWeather`로 매핑. 날씨 enum 규칙: `precipitationMm>0 → "비"`, `else skyCode≥3 → "흐림"`, `else "맑음"`. KMA 실패 시 `Optional.empty()`(업로드는 성공). 이 빈이 존재하면 `NoOpWeatherProvider`(@ConditionalOnMissingBean)는 비활성 → **하루치 업로드가 자동으로 기상청 날씨로 채워진다.**

- [ ] **Step 1: HttpClientConfig (RestClient + HttpServiceProxyFactory) 작성** (backend_spec §2.4 코드).
- [ ] **Step 2: WeatherService 테스트** — KmaForecastPort 목이 tempMax=24, tempMin=18 반환 → `fetchAndStore` 결과 `avgTemp=21.0`, WeatherForecast 저장 확인.
- [ ] **Step 3: 실행 → FAIL** → **Step 4: WeatherService 구현** (KMA 응답 파싱은 Demo 단순화: TMX/TMN/POP/PCP/SKY 추출) → **Step 5: PASS.**
- [ ] **Step 6: KmaWeatherProvider + 날씨 매핑 테스트 작성**

```java
// test/.../weather/KmaWeatherProviderTest.java
@org.springframework.boot.test.context.SpringBootTest @org.springframework.transaction.annotation.Transactional
class KmaWeatherProviderTest {
  @org.springframework.boot.test.mock.mockito.MockBean com.netzero.weather.service.WeatherService weatherService;
  @org.springframework.beans.factory.annotation.Autowired com.netzero.ingest.service.SalesCsvService sales;
  @org.springframework.beans.factory.annotation.Autowired com.netzero.store.repository.SalesRecordRepository repo;
  @org.junit.jupiter.api.Test void dailyUploadEnrichesWeatherFromKma() {
    // given: 기상청 결과(강수 0, 흐림 skyCode=4) → "흐림", avgTemp 21.0
    org.mockito.Mockito.when(weatherService.fetchAndStore(org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.eq(java.time.LocalDate.parse("2026-06-28"))))
      .thenReturn(new com.netzero.weather.dto.WeatherSnapshot(
        java.time.LocalDate.parse("2026-06-28"), new java.math.BigDecimal("21.0"),
        java.math.BigDecimal.ZERO, 10, 4));
    String csv = "날짜,요일,품목,구분,판매수량\n2026-06-28,일,우유,원재료,11\n";
    sales.ingestDaily(1L, new java.io.ByteArrayInputStream(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
        false, false, null);
    var rec = repo.findByStoreIdAndBusinessDateBetween(1L,
        java.time.LocalDate.parse("2026-06-28"), java.time.LocalDate.parse("2026-06-28")).get(0);
    org.assertj.core.api.Assertions.assertThat(rec.getWeather()).isEqualTo("흐림");
    org.assertj.core.api.Assertions.assertThat(rec.getAvgTemp()).isEqualByComparingTo("21.0");
  }
}
```
> `KmaWeatherProvider`가 `@Primary` 빈이므로 SalesCsvService에 주입되어 daily 업로드 시 날씨가 채워진다. (`SalesRecord`에 `getWeather()/getAvgTemp()` getter 필요)

- [ ] **Step 7: 실행 → FAIL → KmaWeatherProvider 구현 → PASS.**

```java
// weather/KmaWeatherProvider.java
package com.netzero.weather;
import com.netzero.weather.dto.DailyWeather; import com.netzero.weather.service.WeatherService;
import org.springframework.context.annotation.Primary; import org.springframework.stereotype.Component;
import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
@Component @Primary
public class KmaWeatherProvider implements WeatherProvider {
  private final WeatherService weather;
  public KmaWeatherProvider(WeatherService w){ this.weather=w; }
  @Override public Optional<DailyWeather> lookup(Long storeId, LocalDate date) {
    try {
      var s = weather.fetchAndStore(storeId, date);
      String wx = (s.precipitationMm()!=null && s.precipitationMm().signum()>0) ? "비"
                : (s.skyCode()!=null && s.skyCode()>=3) ? "흐림" : "맑음";
      return Optional.of(new DailyWeather(wx, s.avgTemp(), s.precipitationMm()));
    } catch (Exception e) { return Optional.empty(); } // 실패 시 업로드는 성공, 날씨 null
  }
}
```

- [ ] **Step 8: WeatherController** `POST /api/v1/weather/refresh` 추가(`ApiResponse.ok(snapshot)`).
- [ ] **Step 9: Commit** `git commit -m "feat: KMA weather client + daily upload weather enrichment (M3)"`.

### Task 3.3: FeatureBuilder

**Files:**
- Create: `feature/FeatureBuilder.java`, `feature/HolidayCalendar.java`, `feature/dto/FeatureVector.java`
- Test: `src/test/java/com/netzero/feature/FeatureBuilderTest.java`

**Interfaces:**
- Consumes: `SalesRecordRepository`.
- Produces: `Map<String,Object> build(Long storeId, Long itemId, LocalDate targetDate)` → `{dayOfWeek, isHoliday, ma7, trend}`. `ma7` = 최근 7일 판매 평균.

- [ ] **Step 1~5: TDD** — 7일 판매 적재 후 ma7 평균 검증, dayOfWeek 0=월 매핑. **Commit** `git commit -m "feat: feature builder (M3)"`.

### Task 3.4: AiForecastClient (RestClient 실연동) — ForecastPort 교체

**Files:**
- Create: `forecast/port/AiForecastClient.java` (`@ConditionalOnProperty(name="ai.mode",havingValue="real")` 또는 기본), config에 RestClient(ai) 빈
- Test: `src/test/java/com/netzero/forecast/AiForecastClientTest.java` (MockRestServiceServer)

**Interfaces:**
- Produces: `AiForecastClient implements ForecastPort` — `POST {ai.base-url}{ai.order-recommendation-path}` 호출, `ForecastRequest`(salesPresignedUrls 포함)→JSON, 응답 역직렬화. resilience4j retry/circuitbreaker, 실패 시 `ApiException(FORECAST_UNAVAILABLE)`.

- [ ] **Step 1: 계약 테스트** — `MockRestServiceServer`로 `/v1/order-recommendation` 응답 픽스처(daily 8개) 제공 → `orderRecommendation(req)`가 itemId·daily 8개 역직렬화 검증. 요청 본문에 `salesHistory.presignedUrls`, `weather[].avgTemp` 포함 검증.
- [ ] **Step 2: 실행 → FAIL** → **Step 3: 구현** → **Step 4: PASS.**
- [ ] **Step 5: Commit** `git commit -m "feat: AiForecastClient via RestClient (M3)"`.

### Task 3.5: S3 아카이빙 + Presign + 월말 스케줄러

**Files:**
- Create: `config/S3Config.java`, `export/service/{S3ArchiveService,PresignService}.java`, `export/scheduler/MonthlyExportScheduler.java`, ExportController에 `POST /export/archive`
- Test: `src/test/java/com/netzero/export/PresignServiceTest.java`, `S3ArchiveServiceIT.java`(LocalStack 선택, 기본은 단위)

**Interfaces:**
- Produces: `S3ArchiveService.upload(String key, String csvBody): void`, `PresignService.presignGet(String key): String` (만료 `storage.s3.presign-expiry-seconds`). `MonthlyExportScheduler` `@Scheduled(cron)` → 말일에 `sales/store{id}/sales-{YYYY-MM}.csv`·`inventory/...` 업로드. `PresignService.recentSalesUrls(Long storeId, LocalDate ref, int months): List<String>`.

- [ ] **Step 1: S3Config** (`S3Client`/`S3Presigner` 빈, endpoint override 지원: MinIO/LocalStack).
- [ ] **Step 2: PresignService 단위 테스트** — 키 규칙 `sales/store1/sales-2026-05.csv` 생성·만료 적용(Presigner 목 or 실제 서명 형식 검증). 
- [ ] **Step 3: 구현** → **Step 4: PASS.**
- [ ] **Step 5: MonthlyExportScheduler + `POST /api/v1/export/archive?storeId=&month=` 수동 트리거.**
- [ ] **Step 6: Commit** `git commit -m "feat: S3 archiving and presigned URLs (M3)"`.

### Task 3.6: DailyPipelineService + `/pipeline/run` + `/forecast`(due) + `/dashboard/summary`

**Files:**
- Create: `pipeline/service/DailyPipelineService.java`, `pipeline/scheduler/PipelineScheduler.java`, `pipeline/controller/PipelineController.java`, `pipeline/dto/PipelineResult.java`, `forecast/controller/ForecastController.java`, `dashboard/service/DashboardService.java`, `dashboard/controller/DashboardController.java`
- Test: `src/test/java/com/netzero/pipeline/DailyPipelineServiceTest.java`

**Interfaces:**
- Consumes: `DueItemSelector`, `WeatherService`, `PresignService`, `OrderOptimizationService`.
- Produces: `DailyPipelineService.run(Long storeId, LocalDate targetDate): PipelineResult` — 0)due 선별 1)날씨 커버기간 수집 2)feature 3)forecast 4)order 5)carbon. 멱등 upsert. `record PipelineResult(Long storeId, LocalDate targetDate, int dueItems, int forecasted, int recommended, int carbonComputed, String modelVersion, long elapsedMs)`. 중복 실행 가드(같은 store+date 진행중) → `PIPELINE_ALREADY_RUNNING`.

- [ ] **Step 1: 통합 테스트** (MockForecastClient) — 시드+재고 적재 후 `run(1L, date)` → dueItems>0, recommended==dueItems, carbon 저장. 재실행 시 멱등(중복 행 없음).
- [ ] **Step 2: 실행 → FAIL** → **Step 3: 구현** → **Step 4: PASS.**
- [ ] **Step 5: 컨트롤러** — `POST /api/v1/pipeline/run`, `GET /api/v1/forecast?storeId=&date=`(due 품목 dueItems[]·skipped[] 형태, backend_spec §5.2), `GET /api/v1/dashboard/summary`.
- [ ] **Step 6: Commit** `git commit -m "feat: daily pipeline, forecast(due) and dashboard (M3)"`.

---

## Phase M4 — 언어계층(챗) + 계측

### Task 4.1: LlmPort + RagContextAssembler + `/chat`

**Files:**
- Create: `chat/port/{LlmPort,AiLlmClient}.java`, `chat/service/{ChatService,RagContextAssembler}.java`, `chat/controller/ChatController.java`, `chat/dto/*`
- Test: `src/test/java/com/netzero/chat/{RagContextAssemblerTest,AiLlmClientTest}.java`

**Interfaces:**
- Consumes: `OrderRecommendationRepository`, `CarbonSavingRepository`, `DemandForecastRepository`, `ItemMasterRepository`.
- Produces:
  - `record Grounding(Map<String,Object> item, Map<String,Object> coverage, Map<String,Object> forecast, Map<String,Object> recommendation, Map<String,Object> carbon, Map<String,Object> context)`.
  - `record LlmRequest(String question, String locale, Grounding grounding)`, `record LlmResponse(String answer, boolean cacheHit, int latencyMs, int tokens)`.
  - `interface LlmPort { LlmResponse generate(LlmRequest req); }`.
  - `RagContextAssembler.assemble(Long storeId, LocalDate date, Long itemId): Grounding` (DB에서 조립).
  - `ChatService.chat(ChatRequest): ChatResponse` (groundedOn ids + cacheHit/llmLatencyMs/tokens).

- [ ] **Step 1: RagContextAssembler 테스트** — 저장된 recommendation/carbon으로 Grounding 조립, 수치 일치 검증.
- [ ] **Step 2: AiLlmClient 계약 테스트** (`MockRestServiceServer` `/v1/generate`) — answer/cacheHit/tokens 역직렬화.
- [ ] **Step 3: 실행 → FAIL** → **Step 4: 구현** (실패 시 `LLM_UNAVAILABLE`) → **Step 5: PASS.**
- [ ] **Step 6: ChatController `POST /api/v1/chat`** + 슬라이스 테스트.
- [ ] **Step 7: Commit** `git commit -m "feat: chat proxy with RAG grounding (M4)"`.

### Task 4.2: 저전력 계측 메트릭 (Micrometer)

**Files:**
- Create: `metrics/ForecastMetrics.java`; chat/pipeline에 계측 주입
- Test: `src/test/java/com/netzero/metrics/ForecastMetricsTest.java` (SimpleMeterRegistry)

**Interfaces:**
- Produces: `ForecastMetrics` with `recordLlmCall(int tokens, long latencyMs, boolean cacheHit)`, `recordPipeline(long ms)`, `recordWape(String itemCode, double wape)` → 메트릭 `zerowave.llm.calls/tokens/latency/cache.hit/miss`, `zerowave.pipeline.duration`, `zerowave.forecast.wape`.

- [ ] **Step 1~4: TDD** — SimpleMeterRegistry로 카운터 증가 검증. ChatService에서 `recordLlmCall` 호출 배선.
- [ ] **Step 5: Commit** `git commit -m "feat: low-power metrics (M4)"`.

---

## Phase M5 — 정확도·시연 마감

### Task 5.1: WAPE 산출 + 대시보드 노출

**Files:**
- Create: `forecast/service/WapeService.java`; DashboardService에 통합
- Test: `src/test/java/com/netzero/forecast/WapeServiceTest.java`

**Interfaces:**
- Produces: `double wape(List<Double> actual, List<Double> predicted)` = `Σ|a−p|/Σa`. 품목별 집계 후 `ForecastMetrics.recordWape`.

- [ ] **Step 1~4: TDD** — `wape([10,8],[9,9]) = (1+1)/18 = 0.111`. **Step 5: Commit** `git commit -m "feat: WAPE accuracy metric (M5)"`.

### Task 5.2: 보안·OpenAPI·CORS 마감 + 데모 스모크

**Files:**
- Create: `config/{SecurityConfig,WebConfig,OpenApiConfig,SchedulingConfig}.java`
- Test: `src/test/java/com/netzero/smoke/DemoSmokeTest.java`

**Interfaces:**
- Produces: API Key(`X-API-Key`) 필터(쓰기 엔드포인트), CORS(프론트 오리진), `@EnableScheduling`, springdoc `/swagger-ui.html`. 스모크: ingest→pipeline(mock)→recommendations→carbon→export 전 과정 1테스트.

- [ ] **Step 1: SecurityConfig** (Demo: 조회는 허용, `/ingest/**`·`/pipeline/**`·`/export/archive` 는 API Key. `X-API-Key`는 config `security.api-key`).
- [ ] **Step 2: DemoSmokeTest 작성** (`@SpringBootTest` + MockMvc, MockForecastClient) — sales/inventory CSV ingest → `pipeline/run` → `GET /recommendations` 비어있지 않음 → `GET /carbon/today` potentialSavingKg>0 → `GET /export/store-inventory.csv` 200.
- [ ] **Step 3: 실행 → PASS** (`./gradlew test`).
- [ ] **Step 4: WebConfig(CORS)/OpenApiConfig/SchedulingConfig 추가.**
- [ ] **Step 5: 전체 빌드** — Run: `./gradlew clean build` → Expected: BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `git commit -m "feat: security, OpenAPI, CORS, demo smoke test (M5)"`.

---

## Self-Review 결과 (작성자 점검)

- **Spec 커버리지:** ItemMaster/OrderPolicy/SalesRecord/InventorySnapshot(원장)·시드(1.1~1.2), 수집(1.4~1.5), 추출+S3(1.6, 3.5), 결정적 산정(2.1~2.3), 포트/AI연동(2.4, 3.4, 4.1), 발주도래(3.1), 날씨avgTemp(3.2), 피처(3.3), 파이프라인/대시보드/forecast(due)(3.6), 챗(4.1), 계측(4.2), WAPE(5.1), 보안/스모크(5.2) — backend_spec·ai_server_api_spec·설계문서 요구 매핑 완료.
- **Placeholder:** 핵심 알고리즘·엔티티·계약은 완전 코드. 반복 패턴(엔티티 매핑, 추가 컨트롤러)은 동일 패턴 명시로 대체했고 시그니처는 Interfaces 블록에 고정.
- **타입 일관성:** `Newsvendor.Quantiles`, `ForecastResponse/ItemForecast/DailyQuantile`, `CarbonResult`, `DueItem`, `Grounding` 등 명칭을 후속 태스크에서 동일 사용. `recommendedOrder`/`optimalStock`/`criticalRatio`/`sumDaily`/`roundToLot` 시그니처 일관.
- **주의(실행자):** AI 응답이 `daily` 대신 `aggregate`인 경우 분기(ai_server_api_spec §3.4)는 Task 2.4/3.4 구현 시 `ItemForecast`에 `aggregate` optional 필드를 추가해 처리(테스트 1건 추가 권장).
