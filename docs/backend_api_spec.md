# Zero-Waste Copilot — 백엔드 REST API 명세

> 팀 **Zero:Wave** · 작성일 2026-06-26
> 본 문서는 프론트엔드가 소비하는 **백엔드 REST API의 단독 계약**이다. 도메인/산정 로직은 [`backend_spec.md`](./backend_spec.md), AI 서버 계약은 [`ai_server_api_spec.md`](./ai_server_api_spec.md) 참고.
> **이 문서가 API 요청/응답 형식의 권위 있는 기준이다.** (응답 envelope은 backend_spec §5.7의 ProblemDetail보다 우선)

---

## 1. 공통 규약

| 항목         | 값                                                         |
| ------------ | ---------------------------------------------------------- |
| 베이스 경로  | `/api/v1`                                                  |
| 포맷         | JSON (UTF-8). CSV 추출만 `text/csv`                        |
| 시간대       | KST(`Asia/Seoul`), 날짜 `YYYY-MM-DD`                       |
| 인증         | 쓰기 엔드포인트는 `X-API-Key` 헤더 필수(Demo). 조회는 허용 |
| 매장         | Demo는 단일 매장(`storeId=1`) 가정                         |
| 페이지네이션 | `?page=&size=` (0-base, 해당 시)                           |

### 1.1 응답 Envelope (일관 구조)

**모든 JSON 응답**은 아래 두 형태 중 하나다. (CSV 다운로드·actuator 제외)

**성공:**

```json
{
  "success": true,
  "data": {
    /* 엔드포인트별 payload */
  }
}
```

**오류:**

```json
{
  "success": false,
  "error": {
    "code": "CONTENT_NOT_FOUND",
    "message": "파일을 찾을 수 없습니다."
  }
}
```

- `success`: 항상 존재(boolean).
- 성공 시 `data`만, 오류 시 `error`만 존재(상호배타).
- `error.code`: 기계 판독용 enum(§1.2). `error.message`: 사용자 표시용 한국어.
- HTTP 상태코드는 `error.code`에 매핑(§1.2). 성공은 200(생성 포함, Demo 단순화).

### 1.2 에러 코드 카탈로그

| code                       | HTTP | message(예시)                         |
| -------------------------- | ---- | ------------------------------------- |
| `VALIDATION_ERROR`         | 400  | 요청 형식이 올바르지 않습니다.        |
| `INVALID_CSV`              | 400  | CSV 형식이 올바르지 않습니다.         |
| `ITEM_NOT_FOUND`           | 400  | 품목을 찾을 수 없습니다.              |
| `STORE_NOT_FOUND`          | 404  | 매장을 찾을 수 없습니다.              |
| `CONTENT_NOT_FOUND`        | 404  | 파일을 찾을 수 없습니다.              |
| `FORECAST_UNAVAILABLE`     | 503  | 수요예측 서버를 사용할 수 없습니다.   |
| `WEATHER_FETCH_FAILED`     | 502  | 날씨 정보를 가져오지 못했습니다.      |
| `LLM_UNAVAILABLE`          | 503  | 챗봇 서버를 사용할 수 없습니다.       |
| `PIPELINE_ALREADY_RUNNING` | 409  | 이미 실행 중인 파이프라인이 있습니다. |
| `INTERNAL_ERROR`           | 500  | 내부 오류가 발생했습니다.             |

> 구현: `ApiResponse<T>` 래퍼(`ok(data)` / `error(code,message)`) + `@RestControllerAdvice`. 부분 성공(CSV 행 일부 거부)은 `success:true`로 두고 `data` 안의 `rejected`/`errors`로 보고.

### 1.3 엔드포인트 요약

| 그룹     | Method | Path                                                          |
| -------- | ------ | ------------------------------------------------------------- |
| 마스터   | GET    | `/items`, `/items/{id}`                                       |
| 재고     | GET    | `/inventory` (날짜별 재고현황)                                |
| 수집     | POST   | `/ingest/sales` (대량·풀컬럼)                                 |
| 수집     | POST   | `/ingest/sales/daily` (하루치·body 메타)                      |
| 수집     | POST   | `/ingest/inventory`                                           |
| 수집     | POST   | `/weather/refresh`                                            |
| 분석     | POST   | `/pipeline/run`                                               |
| 분석     | GET    | `/forecast`                                                   |
| 분석     | GET    | `/recommendations`, `/recommendations/{id}`                   |
| 분석     | PUT    | `/recommendations/actual` (실제 발주량 입력)                  |
| 탄소     | GET    | `/carbon/today`, `/carbon/savings`, `/carbon/savings/summary` |
| 대시보드 | GET    | `/dashboard/summary`                                          |
| 챗       | POST   | `/chat`                                                       |
| 추출     | GET    | `/export/sales.csv`, `/export/store-inventory.csv`            |
| 추출     | POST   | `/export/archive`                                             |
| 운영     | GET    | `/actuator/health`, `/swagger-ui.html`                        |

---

## 2. 데이터 수집 API

### 2.1 `POST /api/v1/ingest/sales` — 판매내역 대량 업로드

풀컬럼 CSV(여러 날·날씨 포함). 합성/과거 데이터 일괄 적재용.
**Request** — `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | file(CSV) | ✅ | 헤더: `날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오` |
| `storeId` | int | ✅ | 매장 ID |

**Response 200** (부분 성공 허용)

```json
{
  "success": true,
  "data": {
    "accepted": 360,
    "rejected": 2,
    "errors": [{ "line": 15, "code": "ITEM_NOT_FOUND", "value": "없는품목" }]
  }
}
```

### 2.2 `POST /api/v1/ingest/sales/daily` — 하루치 판매내역 업로드 (신규)

하루치 판매를 담은 CSV(날씨 컬럼 없음). **행사/신메뉴/비고\_시나리오는 품목(행)별로** CSV 컬럼에 담는다(그날 전체 일괄 적용 아님).

**Request** — `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | file(CSV) | ✅ | 헤더: `날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오` |
| `storeId` | int | ✅ | 매장 ID |

CSV 예(품목별로 행사/신메뉴/시나리오가 다름):

```
날짜,요일,품목,구분,판매수량,행사,신메뉴,비고_시나리오
2026-06-28,일,우유,원재료,11,,,주말+맑음->수요 높음
2026-06-28,일,베이커리,완제품,23,Y,Y,신메뉴 출시+행사로 수요 급증
```

> **품목별 메타:** 각 행의 `행사`/`신메뉴`(여부, 예: `Y`/공백)는 `SalesRecord.event/newMenu`에, `비고_시나리오`는 `SalesRecord.scenarioNote`에 **행 단위로** 저장된다.
> **날씨 자동 보강:** CSV엔 날씨 컬럼이 없고, 업로드 시 **백엔드가 기상청 API로 해당 날짜 날씨를 조회**해 `SalesRecord.weather/avgTemp/precipitationMm`를 채우고 `WeatherForecast`에도 저장한다. 매핑: `precipitationMm>0 → 비`, `else skyCode≥3 → 흐림`, `else 맑음`. 기상청 조회 실패 시 날씨 필드는 null(업로드는 성공).
> 적용 날짜는 CSV `날짜`(보통 단일일).

**Response 200**

```json
{
  "success": true,
  "data": {
    "appliedDate": "2026-06-28",
    "accepted": 2,
    "rejected": 0,
    "errors": []
  }
}
```

**오류 예** (CSV 헤더 자체가 깨진 경우):

```json
{
  "success": false,
  "error": { "code": "INVALID_CSV", "message": "CSV 헤더가 올바르지 않습니다." }
}
```

### 2.3 `POST /api/v1/ingest/inventory` — 재고내역 업로드

**Request** — `multipart/form-data`

| 파트 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `file` | file(CSV) | ✅ | 헤더: `날짜,품목,구분,단위,발주,기초재고,수요,실판매,결품,폐기,기말재고` |
| `storeId` | int | ✅ | 매장 ID |

**Response 200**

```json
{ "success": true, "data": { "accepted": 240, "rejected": 0, "errors": [] } }
```

> 폐기*kg·탄소\_kgCO2e·폐기*비용\_원은 적재 시 ItemMaster로 계산되어 저장(backend_spec §3.2).

### 2.4 `POST /api/v1/weather/refresh` — 기상청 예보 즉시 수집

**Request** — `application/json`

```json
{ "storeId": 1, "date": "2026-06-28" }
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "region": "서울_강남",
    "forecastDate": "2026-06-28",
    "avgTemp": 21.2,
    "precipitationMm": 12.0,
    "precipitationProb": 80,
    "skyCode": 4
  }
}
```

**오류:** `WEATHER_FETCH_FAILED`(502).

---

## 3. 분석 API

### 3.1 `POST /api/v1/pipeline/run` — 일일 파이프라인 실행

발주 도래 품목 선별 → 예측 → 발주 → 탄소 산정.
**Request** — `application/json`

```json
{ "storeId": 1, "targetDate": "2026-06-27" }
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "storeId": 1,
    "targetDate": "2026-06-27",
    "dueItems": 5,
    "forecasted": 5,
    "recommended": 5,
    "carbonComputed": 5,
    "modelVersion": "lgbm_global_v1",
    "elapsedMs": 214
  }
}
```

**오류:** `PIPELINE_ALREADY_RUNNING`(409), `FORECAST_UNAVAILABLE`(503).

### 3.2 `GET /api/v1/forecast` — 발주 도래 품목 예측 조회

OrderPolicy·InventorySnapshot로 발주 도래 품목만 골라 커버기간(리드타임+발주주기) 예측. 도래 판정: `date − lastOrderDate ≥ orderCycleDays`(lastOrderDate 없으면 도래), OrderPolicy 없는 품목 제외.
**Request** — query: `storeId`(int, 필수), `date`(date, 필수)
**Response 200**

```json
{
  "success": true,
  "data": {
    "storeId": 1,
    "targetDate": "2026-06-27",
    "dueItems": [
      {
        "itemId": 101,
        "itemName": "우유",
        "coverage": {
          "leadTimeDays": 1,
          "orderCycleDays": 7,
          "coverageDays": 8
        },
        "lastOrderDate": "2026-06-19",
        "daysSinceLastOrder": 8,
        "horizonForecast": { "p10": 60, "p50": 80, "p90": 108 },
        "daily": [{ "date": "2026-06-28", "p10": 6, "p50": 8, "p90": 11 }],
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
}
```

### 3.3 `GET /api/v1/recommendations` — 발주 추천 목록

**Request** — query: `storeId`(필수), `date`(필수)
**Response 200**

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "itemId": 101,
        "itemName": "우유",
        "unit": "L",
        "coverage": {
          "leadTimeDays": 1,
          "orderCycleDays": 7,
          "coverageDays": 8
        },
        "horizonForecast": { "p10": 60, "p50": 80, "p90": 108 },
        "recommendedQuantity": 66,
        "actualQuantity": 60,
        "optimalStockQuantity": 76.1,
        "onHand": 12,
        "baselineQuantity": 88,
        "criticalRatio": 0.421,
        "expectedWasteAvoidedKg": 12.31,
        "rationale": {
          "cu": 800,
          "co": 1100,
          "lotUnit": 2,
          "interpolation": "P10h~P50h @0.803"
        }
      }
    ]
  }
}
```

> `recommendedQuantity`는 시스템 추천값, `actualQuantity`는 사용자가 확정한 실제 발주량(§3.5로 입력, **미입력 시 `null`**). 둘을 분리 기록해 추천 대비 실제 사용을 비교한다.

### 3.4 `GET /api/v1/recommendations/{id}` — 발주 추천 단건

**Request** — path: `id`(long)
**Response 200**: `data`는 §3.3 `items[]` 단건 객체 + 전체 `rationale`.
**오류:** `CONTENT_NOT_FOUND`(404).

### 3.5 `PUT /api/v1/recommendations/actual` — 품목별 실제 발주량 입력/수정

사용자가 추천 발주량을 그대로 쓰거나 **직접 수정한 실제 발주량**을 품목별로 확정한다. `OrderRecommendation.actualQuantity`만 갱신하며 `recommendedQuantity`(시스템 추천)는 보존한다. **쓰기 → `X-API-Key` 필수.**
**Request** — `application/json`

```json
{
  "storeId": 1,
  "targetDate": "2026-06-27",
  "items": [
    { "itemId": 101, "actualQuantity": 60 },
    { "itemId": 205, "actualQuantity": 0 }
  ]
}
```

| 필드                     | 타입   | 필수 | 설명                                            |
| ------------------------ | ------ | ---- | ----------------------------------------------- |
| `storeId`                | int    | ✅   | 매장 ID                                         |
| `targetDate`             | date   | ✅   | 추천이 산출된 날짜(`OrderRecommendation` 키)    |
| `items[].itemId`         | int    | ✅   | 품목 ID                                         |
| `items[].actualQuantity` | number | ✅   | 사용자 확정 실제 발주량(≥0). `0`은 "발주 안 함" |

**Response 200** (부분 성공 허용 — 매칭 안 된 품목은 `notFound`)

```json
{
  "success": true,
  "data": {
    "storeId": 1,
    "targetDate": "2026-06-27",
    "updated": 2,
    "notFound": [],
    "items": [
      {
        "itemId": 101,
        "itemName": "우유",
        "recommendedQuantity": 66,
        "actualQuantity": 60
      },
      {
        "itemId": 205,
        "itemName": "원두",
        "recommendedQuantity": 12,
        "actualQuantity": 0
      }
    ]
  }
}
```

> 매칭되는 추천행(`store+item+targetDate`)이 없는 `itemId`는 `notFound[]`에 담고 나머지는 적용(`success:true`). `actualUpdatedAt`은 서버가 현재 KST로 기록.
> **오류:** `actualQuantity<0`/형식 오류 → `VALIDATION_ERROR`(400), 매장 없음 → `STORE_NOT_FOUND`(404).

---

## 4. 탄소 리포트 API

### 4.1 `GET /api/v1/carbon/today` — 오늘의 절감 탄소

**Request** — query: `storeId`(필수)
**Response 200**

```json
{
  "success": true,
  "data": {
    "storeId": 1,
    "targetDate": "2026-06-27",
    "guaranteedSavingKg": 2.46,
    "potentialSavingKg": 39.4,
    "wasteCostAvoidedKrw": 11950,
    "carEquivalentKm": 8.6,
    "byItem": [
      {
        "itemId": 101,
        "itemName": "우유",
        "wasteAvoidedKg": 12.31,
        "guaranteedSavingKg": 2.46,
        "potentialSavingKg": 39.4
      }
    ]
  }
}
```

### 4.2 `GET /api/v1/carbon/savings` — 기간 절감 시계열

**Request** — query: `storeId`(필수), `from`(date), `to`(date)
**Response 200**

```json
{
  "success": true,
  "data": {
    "series": [
      {
        "date": "2026-06-25",
        "guaranteedSavingKg": 2.1,
        "potentialSavingKg": 33.0
      },
      {
        "date": "2026-06-26",
        "guaranteedSavingKg": 2.4,
        "potentialSavingKg": 38.2
      }
    ]
  }
}
```

### 4.3 `GET /api/v1/carbon/savings/summary` — 누적 요약

**Request** — query: `storeId`(필수)
**Response 200**

```json
{
  "success": true,
  "data": {
    "totalGuaranteedKg": 64.2,
    "totalPotentialKg": 980.5,
    "carEquivalentKm": 213.2,
    "periodDays": 30
  }
}
```

---

## 5. 대시보드 API

### 5.1 `GET /api/v1/dashboard/summary`

**Request** — query: `storeId`(필수)
**Response 200**

```json
{
  "success": true,
  "data": {
    "today": {
      "targetDate": "2026-06-27",
      "dueItemCount": 5,
      "potentialSavingKg": 39.4,
      "guaranteedSavingKg": 2.46
    },
    "orderGuide": [
      {
        "itemId": 101,
        "itemName": "우유",
        "recommendedQuantity": 66,
        "unit": "L"
      }
    ],
    "accuracy": [{ "itemId": 101, "wape": 0.18 }]
  }
}
```

---

## 6. 챗 API

### 6.1 `POST /api/v1/chat` — 자연어 질의(sLLM 프록시)

**Request** — `application/json`

```json
{ "storeId": 1, "date": "2026-06-27", "question": "내일 우유 얼마나 시켜요?" }
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "answer": "다음 발주주기(7일)에 우유는 66L 발주를 권장합니다. ... 약 39kgCO₂eq를 절감합니다.",
    "groundedOn": {
      "forecastId": 123,
      "recommendationId": 456,
      "carbonSavingId": 789
    },
    "cacheHit": true,
    "llmLatencyMs": 180,
    "tokens": 142
  }
}
```

> sLLM에 근거 수치를 주입만 하고 생성은 맡기지 않는다(환각 차단). 백엔드는 `cacheHit/tokens/latency`를 계측 메트릭으로 중계.
> **오류:** `LLM_UNAVAILABLE`(503).

---

## 7. 데이터 추출 API

### 7.1 `GET /api/v1/export/sales.csv` — 판매내역 월간 CSV

**Request** — query: `storeId`(필수), `month`(`YYYY-MM`), `format`(`csv|xlsx`, 기본 csv)
**Response 200** — **envelope 미적용(파일 다운로드).** `Content-Type: text/csv; charset=UTF-8`(BOM), `Content-Disposition: attachment; filename=sales-2026-06.csv`. 본문은 CSV(컬럼: `날짜,요일,날씨,기온,강수mm,행사,신메뉴,품목,구분,판매수량,비고_시나리오`).
**오류(JSON envelope):** 잘못된 month → `VALIDATION_ERROR`(400), 데이터 없음 → `CONTENT_NOT_FOUND`(404).

### 7.2 `GET /api/v1/export/store-inventory.csv` — 재고흐름 월간 CSV

**Request** — query: `storeId`(필수), `month`, `format`.
**Response 200** — §7.1과 동일 형식. 파일명 `store-inventory-2026-06.csv`, 컬럼 `날짜,요일,품목,구분,단위,발주,기초재고,수요,실판매,결품,폐기,기말재고,폐기_kg,탄소_kgCO2e,폐기_비용_원`.

### 7.3 `POST /api/v1/export/archive` — 월간 CSV S3 아카이빙(수동 트리거)

**Request** — `application/json`

```json
{ "storeId": 1, "month": "2026-06" }
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "salesKey": "sales/store1/sales-2026-06.csv",
    "inventoryKey": "inventory/store1/store-inventory-2026-06.csv"
  }
}
```

---

## 8. 마스터 조회 API

품목마스터(ItemMaster)는 매장 공통 기준정보다. 프론트의 품목 선택·필터·단위/EF 표시에 사용한다.

### 8.1 `GET /api/v1/items` — 품목마스터 전체 조회

등록된 모든 품목을 반환한다. 페이지네이션 없이 전체 목록(Demo 품목 수가 적음).
**Request** — query (모두 선택)

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | string | — | 구분 필터(`완제품\|원재료\|판매음료\|소모품`). 생략 시 전체 |
| `wasteTargetOnly` | boolean | — | `true`면 폐기대상(`wasteTarget=true`) 품목만 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "count": 6,
    "items": [
      {
        "itemId": 101,
        "name": "우유",
        "category": "원재료",
        "wasteTarget": true,
        "unit": "L",
        "shelfLifeDays": 7,
        "storageCondition": "냉장",
        "kgPerUnit": 1.03,
        "efProd": 3.0,
        "efWaste": 0.2,
        "purchasePrice": 1000,
        "priceUnit": "원/L",
        "note": "EF: OWID/Poore2018 우유≈3"
      }
    ]
  }
}
```

> 정렬: `category` 후 `name` 오름차순. 금액/계수는 숫자(JSON number), 한글 enum 문자열 그대로 노출.

### 8.2 `GET /api/v1/items/{id}` — 품목마스터 단건

**Request** — path: `id`(long)
**Response 200**: `data`는 §8.1 `items[]` 단건 객체.
**오류:** `ITEM_NOT_FOUND`(400).

---

## 9. 재고 조회 API

`InventorySnapshot`(일별 재고 원장)을 날짜로 조회한다. 프론트의 재고현황 화면·발주 판단 보조에 사용한다.

### 9.1 `GET /api/v1/inventory` — 특정 날짜 재고현황 조회

요청 날짜(`date`)의 품목별 재고 원장 행과 그날 합계를 반환한다.
**Request** — query

| 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `storeId` | int | ✅ | 매장 ID |
| `date` | date | ✅ | 조회 날짜(`YYYY-MM-DD`) |
| `category` | string | — | 구분 필터(`완제품\|원재료\|판매음료\|소모품`). 생략 시 전체 |
| `wasteTargetOnly` | boolean | — | `true`면 폐기대상 품목만 |

**Response 200**

```json
{
  "success": true,
  "data": {
    "storeId": 1,
    "businessDate": "2026-06-27",
    "dayOfWeek": "토",
    "itemCount": 6,
    "summary": {
      "totalWasteKg": 3.21,
      "totalWasteCarbonKg": 9.84,
      "totalWasteCostKrw": 11950
    },
    "items": [
      {
        "itemId": 101,
        "itemName": "우유",
        "category": "원재료",
        "unit": "L",
        "orderedQty": 0,
        "openingStock": 12,
        "demand": 9,
        "actualSales": 9,
        "stockout": 0,
        "wasteQty": 1,
        "closingStock": 2,
        "wasteKg": 1.03,
        "wasteCarbonKg": 3.296,
        "wasteCostKrw": 1000,
        "lastOrderDate": "2026-06-26"
      }
    ]
  }
}
```

> 정렬: `category` 후 `itemName` 오름차순. `summary`는 조회 결과(필터 적용 후) 행들의 폐기 합계.
> 해당 날짜 스냅샷이 없으면 `success:true`, `itemCount:0`, `items:[]`, `summary`는 합계 0(404 아님 — 빈 현황도 정상).
> **오류:** 날짜 형식 오류 → `VALIDATION_ERROR`(400), 매장 없음 → `STORE_NOT_FOUND`(404).

---

## 10. 운영

- `GET /actuator/health` — `{ "status": "UP" }` (actuator 표준, envelope 미적용).
- `GET /actuator/metrics`, `/actuator/prometheus` — 계측.
- `GET /swagger-ui.html`, `/v3/api-docs` — OpenAPI 문서.

---

## 부록 — 변경 이력

- 신규 `PUT /recommendations/actual` — 품목별 **실제 발주량** 입력/수정. `OrderRecommendation`에 `actualQuantity`(사용자 실제)·`actualUpdatedAt` 추가, `recommendedQuantity`(시스템 추천)와 분리 기록. §3.3/§3.4 응답에 `actualQuantity`(미입력 시 `null`) 노출.
- 신규 `GET /inventory` — 특정 날짜 재고현황 조회(InventorySnapshot 투영, 품목별 행 + 그날 폐기 합계). 운영 섹션은 §9→§10으로 이동.
- 신규 `GET /items`, `GET /items/{id}` — 품목마스터(ItemMaster) 조회 API(`category`/`wasteTargetOnly` 필터). 운영 섹션은 §8→§9로 이동.
- 신규 `POST /ingest/sales/daily` (하루치 CSV; 행사/신메뉴/비고\_시나리오는 품목별 CSV 컬럼, 날씨는 기상청 자동 보강).
- 응답 형식을 **`{success,data}` / `{success,error{code,message}}`** envelope으로 일관화(기존 ProblemDetail 대체).
- 각 API에 Request/Response JSON 예시 명시.
