## REST API (요약)

베이스 `/api/v1` · JSON · KST. 상세 요청/응답은 [`backend_spec.md` §5](../../backend_spec.md).

| 그룹     | 엔드포인트                                                                                 |
| -------- | ------------------------------------------------------------------------------------------ |
| 수집     | `POST /ingest/pos`, `POST /ingest/inventory`, `POST /weather/refresh`                      |
| 분석     | `POST /pipeline/run`, `GET /forecast`, `GET /recommendations`, `GET /recommendations/{id}` |
| 탄소     | `GET /carbon/today`, `GET /carbon/savings`, `GET /carbon/savings/summary`                  |
| 대시보드 | `GET /dashboard/summary`                                                                   |
| 챗       | `POST /chat`, `GET /chat/context`                                                          |
| 운영     | `/actuator/*`, `/swagger-ui.html`, `/v3/api-docs`                                          |

## REST API 명세

베이스: `/api/v1` · 포맷: JSON · 시간: ISO-8601(KST, `Asia/Seoul`) · 인증: §7 · 페이지네이션: `?page=&size=` (0-base).

> 향후 버전관리는 Spring Boot 3.5 네이티브 **API versioning**(헤더/경로/쿼리)으로 이행 가능. 해커톤은 경로 `/v1` 고정.

### 5.1 데이터 수집

**`POST /api/v1/ingest/sales`** — multipart `file`(CSV), `storeId` (구 `/ingest/pos`)

```
CSV 헤더: 날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오
Res 200: { "accepted": 360, "rejected": 2, "errors":[{"line":15,"reason":"INVALID_CATEGORY"}] }
```

**`POST /api/v1/ingest/inventory`** — multipart `file`, `storeId`

```
CSV 헤더: 날짜,품목,구분,단위,발주,기초재고,수요,실판매,결품,폐기,기말재고
```

**`POST /api/v1/weather/refresh`** — `{ "storeId":1, "date":"2026-06-27" }` → 기상청 즉시 수집 → `WeatherForecast`

> 품목은 `품목ID` 또는 `품목명`으로 매칭(ItemMaster). 미존재 시 행 거부(`INVALID_CATEGORY`).

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
{
  "storeId": 1,
  "targetDate": "2026-06-27",
  "dueItems": [
    {
      "itemId": 101,
      "itemName": "우유",
      "coverage": { "leadTimeDays": 1, "orderCycleDays": 7, "coverageDays": 8 },
      "lastOrderDate": "2026-06-19",
      "daysSinceLastOrder": 8,
      "horizonForecast": { "p10": 60, "p50": 80, "p90": 108 },
      "daily": [{ "date": "2026-06-28", "p10": 6, "p50": 8, "p90": 11 }, "..."],
      "modelVersion": "lgbm_global_v1"
    }
  ],
  "skipped": [
    {
      "itemId": 205,
      "itemName": "원두",
      "reason": "NOT_DUE",
      "daysSinceLastOrder": 3,
      "orderCycleDays": 14
    }
  ]
}
```

> `POST /pipeline/run`도 동일한 **발주 도래 품목 선별** 로직을 사용한다(도래 품목만 예측→발주→탄소 산정).
> **`GET /api/v1/recommendations?storeId=1&date=2026-06-27`**

```json
{
  "items": [
    {
      "itemId": 101,
      "itemName": "우유",
      "unit": "L",
      "recommendedQuantity": 9.0,
      "optimalStockQuantity": 11.0,
      "onHand": 2.0,
      "baselineQuantity": 12.0,
      "criticalRatio": 0.42,
      "expectedWasteAvoidedKg": 3.09,
      "rationale": {
        "reason": "rain_forecast",
        "precipitationProb": 80,
        "cu": 1200,
        "co": 1650
      }
    }
  ]
}
```

**`GET /api/v1/recommendations/{id}`** — 단건 + 전체 rationale.

### 5.3 탄소 리포트

**`GET /api/v1/carbon/today?storeId=1`**

```json
{
  "storeId": 1,
  "targetDate": "2026-06-27",
  "guaranteedSavingKg": 2.1,
  "potentialSavingKg": 9.4,
  "byItem": [
    {
      "itemId": 101,
      "itemName": "우유",
      "wasteAvoidedKg": 3.09,
      "guaranteedSavingKg": 0.6,
      "potentialSavingKg": 9.3
    }
  ],
  "carEquivalentKm": 2.0
}
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

### 5.7 표준 에러 포맷 (RFC 7807 호환 + code)

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "code": "INVALID_CSV",
  "detail": "품목 'XYZ' not found at line 15",
  "instance": "/api/v1/ingest/sales",
  "timestamp": "2026-06-26T10:00:00+09:00"
}
```

**에러 코드 카탈로그:** `INVALID_CSV`, `INVALID_CATEGORY`, `STORE_NOT_FOUND`, `FORECAST_UNAVAILABLE`, `WEATHER_FETCH_FAILED`, `LLM_UNAVAILABLE`, `PIPELINE_ALREADY_RUNNING`, `VALIDATION_ERROR`.

- `@RestControllerAdvice` + `ProblemDetail`(Spring 6) 사용.

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

> 산출 컬럼(결품·폐기·폐기\_kg·탄소·비용)은 InventorySnapshot 적재 시 계산됨(§3.2 InventorySnapshot 규칙 참조).
