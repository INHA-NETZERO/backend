# Zero-Waste Copilot — AI 서버 API 명세 (계약)

> 팀 **Zero:Wave** · 작성일 2026-06-26
> 본 문서는 **Python AI 서버(별도 레포)** 가 구현해야 할 HTTP API 계약이다.
> 소비자는 Spring 백엔드(본 레포). 관련 문서: [`backend_spec.md`](./backend_spec.md), [설계문서](./superpowers/specs/2026-06-26-zerowave-backend-design.md).

---

## 1. 역할과 경계

AI 서버는 **두 가지 추론만** 담당하는 **상태 없는(stateless) 서비스**다.
1. **수요예측** — LightGBM 글로벌 모델로 품목별 분위예측(p10/p50/p90) 산출.
2. **자연어 설명(sLLM)** — 백엔드가 만든 정량 근거를 **문장으로만** 변환(수치 생성 금지).

**하지 않는 것 (백엔드 책임):**
- DB 접근 / 데이터 영속화 (AI 서버는 요청 본문으로만 데이터 수신).
- 발주 최적화(newsvendor)·탄소 산정·발주도래 판정 등 **결정적 계산**.
- 수치 생성 — sLLM은 받은 근거 수치를 바꾸거나 새로 만들지 않는다(환각 차단).

> **설계 원칙:** "수치는 백엔드 결정적 로직, 언어/예측 추론은 AI 서버". 모든 발주·탄소 수치의 최종 산출은 백엔드가 한다.

---

## 2. 공통 규약

| 항목 | 값 |
|------|------|
| 권장 스택 | Python 3.10+, FastAPI, HF Transformers + 양자화(BitsAndBytes 4/8-bit), LightGBM |
| Base URL | 백엔드 설정 `ai.base-url` (기본 `http://localhost:8000`) |
| 프로토콜 | HTTP/1.1, JSON (UTF-8) |
| 인증 | Demo: 없음 또는 `X-API-Key`(백엔드와 공유). Prod: 내부망 한정 |
| 시간 형식 | `date`: `YYYY-MM-DD` (KST 기준 날짜) |
| 수량 단위 | 품목 관리단위(L/ea/kg 등) — 백엔드가 ItemMaster 기준으로 해석 |
| 타임아웃(백엔드측) | connect 3s / read 5s, retry 1회 + circuitbreaker |

**엔드포인트 요약**
| Method | Path | 용도 |
|---|---|---|
| POST | `/v1/order-recommendation` | **발주용** 커버기간(리드타임+발주주기) 품목별 분위 수요예측 |
| POST | `/v1/forecast` | **다음날 단일 수요예측** (발주와 무관, 대시보드 표시용) |
| POST | `/v1/generate` | 근거 기반 자연어 답변(sLLM) |
| GET | `/health` | 헬스체크 |
| GET | `/metrics` | (선택) Prometheus 계측 |

> 두 예측 엔드포인트 구분: **`/v1/order-recommendation`** 은 커버기간 합산 발주를 위한 다일 예측, **`/v1/forecast`** 는 익일 수요 표시만 위한 단일일 예측이다.

---

## 3. `POST /v1/order-recommendation` — 발주용 수요예측 (커버기간)

발주 도래 품목들에 대해, **커버기간(리드타임+발주주기) 일별 분위예측**을 반환한다.
백엔드가 일별 예측을 합산(Σ)해 발주량(newsvendor)을 산출하므로, AI는 **일자별 p10/p50/p90**를 주는 것이 기본이다.
> 이 엔드포인트는 발주 산출 전용이다. 발주와 무관한 단순 익일 수요는 §4 `/v1/forecast`를 쓴다.

### 3.1 요청 (Request)
```jsonc
POST /v1/order-recommendation
{
  "storeId": 1,
  "targetDate": "2026-06-27",                  // 발주 기준일(오늘)
  "coverage": {                                // 커버기간 = 리드타임 + 발주주기
    "leadTimeDays": 1,
    "orderCycleDays": 7,
    "coverageDays": 8                           // = leadTimeDays + orderCycleDays
  },
  "weather": [                                  // 커버기간 일별 날씨(가용분), 기온 = 평균온도
    { "forecastDate": "2026-06-28", "avgTemp": 21.2,
      "precipitationMm": 12.0, "precipitationProb": 80, "skyCode": 4 }
    // ... coverageDays 개까지(부족하면 가용분만)
  ],
  "rows": [                                     // 예측 대상 품목들(발주 도래 품목)
    {
      "itemId": 101,
      "orderCycleDays": 7,                      // 품목별 정책(coverage와 일관)
      "leadTimeDays": 1,
      "features": {                             // 품목별 정형 피처
        "dayOfWeek": 6,                         // 0=월 ... 6=일 (target+1 기준)
        "isHoliday": false,
        "ma7": 9.4,                             // 최근 7일 이동평균 판매량
        "trend": -0.3                           // 단기 추세(선택)
      }
    }
  ]
}
```

**필드 정의**
| 경로 | 타입 | 필수 | 설명 |
|---|---|---|---|
| storeId | int | ✅ | 매장 ID (Demo: 단일 매장) |
| targetDate | date | ✅ | 발주 기준일 |
| coverage.leadTimeDays | int | ✅ | 리드타임(일) |
| coverage.orderCycleDays | int | ✅ | 발주주기(일) |
| coverage.coverageDays | int | ✅ | = leadTimeDays + orderCycleDays |
| weather[] | array | ✅ | 커버기간 일별 날씨(가용분). **기온은 평균온도 `avgTemp`** |
| weather[].avgTemp | number | ✅ | 평균온도(℃). tempMax/tempMin은 전달하지 않음 |
| weather[].precipitationMm | number | ✅ | 강수량(mm) |
| weather[].precipitationProb | int | ✅ | 강수확률(0~100) |
| weather[].skyCode | int | ✅ | 하늘상태(1맑음~4흐림) |
| rows[] | array | ✅ | 예측 대상 품목 목록 |
| rows[].itemId | int | ✅ | 품목 ID(ItemMaster) |
| rows[].features | object | ✅ | 정형 피처(dayOfWeek/isHoliday/ma7/trend 등) |

### 3.2 응답 (Response)
```jsonc
200 OK
{
  "modelVersion": "lgbm_global_v1",            // 또는 baseline_v1 (폴백)
  "predictions": [
    {
      "itemId": 101,
      "daily": [                               // coverageDays 개 일별 분위
        { "date": "2026-06-28", "p10": 6, "p50": 8,  "p90": 11 },
        { "date": "2026-06-29", "p10": 7, "p50": 9,  "p90": 12 }
        // ...
      ]
    }
  ]
}
```

**필드 정의**
| 경로 | 타입 | 설명 |
|---|---|---|
| modelVersion | string | `lgbm_global_v1`(학습 모델) 또는 `baseline_v1`(폴백) |
| predictions[].itemId | int | 요청 itemId 그대로 |
| predictions[].daily[] | array | 커버기간 일별 분위 배열 |
| daily[].date | date | 예측 대상일(targetDate 다음날부터 coverageDays개) |
| daily[].p10/p50/p90 | number | 해당일 수요 분위(관리단위), `p10 ≤ p50 ≤ p90` |

**제약**
- `p10 ≤ p50 ≤ p90`, 모두 `≥ 0`.
- `daily` 길이는 `coverageDays`와 같게 반환(가능한 경우). 날씨 결측일은 AI가 보정.
- 요청 `rows`의 모든 `itemId`에 대해 응답 포함.

### 3.3 폴백(baseline) — LightGBM 미준비 시
모델이 아직 없거나 추론 실패 시 **AI 서버가 단순 베이스라인을 반환**한다(백엔드는 자체 폴백 없음).
- 규칙(예): 요일×품목 `ma7` 기반 점추정 → 분위는 `p50=ma7`, `p10=0.8·p50`, `p90=1.3·p50` 같은 고정 배수.
- `modelVersion`을 **`baseline_v1`** 로 표기해 백엔드/대시보드가 구분 가능하게 한다.

### 3.4 단일 집계 분위 대체(선택, Demo)
일별 배열이 어려운 모델이면, 커버기간 **집계 단일 분위**도 허용한다(백엔드가 그대로 사용).
```jsonc
"predictions": [{ "itemId":101, "aggregate": { "p10":60, "p50":80, "p90":108 } }]
```
> `daily` 또는 `aggregate` 중 하나는 반드시 포함. 둘 다 있으면 `daily` 우선.

---

## 4. `POST /v1/forecast` — 다음날 수요예측 (발주 무관)

**발주 로직과 무관하게 다음날(단일일) 수요만** 예측한다. 대시보드의 '내일 예상 수요' 표시 등에 사용하며, 커버기간·발주주기·리드타임 개념이 없다.

### 4.1 요청 (Request)
```jsonc
POST /v1/forecast
{
  "storeId": 1,
  "targetDate": "2026-06-28",                  // 예측 대상일(다음날)
  "weather": {                                 // 단일일 날씨, 기온 = 평균온도
    "forecastDate": "2026-06-28", "avgTemp": 21.2,
    "precipitationMm": 12.0, "precipitationProb": 80, "skyCode": 4
  },
  "rows": [
    { "itemId": 101,
      "features": { "dayOfWeek": 6, "isHoliday": false, "ma7": 9.4, "trend": -0.3 } }
  ]
}
```
> §3과 차이: `coverage` 없음, `weather`는 **배열이 아닌 단일 객체**, `rows[]`에 발주정책(orderCycleDays/leadTimeDays) 불필요.

| 경로 | 타입 | 필수 | 설명 |
|---|---|---|---|
| storeId | int | ✅ | 매장 ID |
| targetDate | date | ✅ | 예측 대상일(다음날) |
| weather | object | ✅ | 대상일 단일 날씨. 기온은 평균온도 `avgTemp` |
| rows[].itemId | int | ✅ | 품목 ID |
| rows[].features | object | ✅ | 정형 피처 |

### 4.2 응답 (Response)
```jsonc
200 OK
{
  "modelVersion": "lgbm_global_v1",            // 또는 baseline_v1
  "targetDate": "2026-06-28",
  "predictions": [
    { "itemId": 101, "p10": 6, "p50": 8, "p90": 11 }   // 단일일 분위(daily 배열 아님)
  ]
}
```
| 경로 | 타입 | 설명 |
|---|---|---|
| predictions[].itemId | int | 요청 itemId 그대로 |
| predictions[].p10/p50/p90 | number | **대상일 단일** 수요 분위, `p10 ≤ p50 ≤ p90 ≥ 0` |

### 4.3 폴백
§3.3과 동일 규칙으로 `baseline_v1` 단일일 분위 반환.

---

## 5. `POST /v1/generate` — 자연어 설명 (sLLM)

백엔드가 조립한 **근거 번들(grounding)** 을 받아 2~3문장 한국어 설명을 생성한다.
**근거 수치를 그대로 인용**하고, 새 수치를 만들거나 바꾸지 않는다.

### 5.1 요청 (Request)
```jsonc
POST /v1/generate
{
  "question": "내일 우유 얼마나 시켜요?",
  "locale": "ko",
  "grounding": {
    "item": { "itemId": 101, "itemName": "우유", "unit": "L" },
    "coverage": { "leadTimeDays": 1, "orderCycleDays": 7, "coverageDays": 8 },
    "forecast": { "p10": 60, "p50": 80, "p90": 108 },
    "recommendation": {
      "recommendedQuantity": 66, "optimalStockQuantity": 76.1, "onHand": 12,
      "baselineQuantity": 88, "criticalRatio": 0.421
    },
    "carbon": {
      "wasteAvoidedKg": 12.31, "guaranteedSavingKg": 2.46,
      "potentialSavingKg": 39.4, "carEquivalentKm": 8.6
    },
    "context": { "weatherSummary": "비(강수확률 80%)", "reason": "weekend_demand_up" }
  }
}
```
| 경로 | 타입 | 필수 | 설명 |
|---|---|---|---|
| question | string | ✅ | 사용자 자연어 질문 |
| locale | string | ✅ | 기본 `ko` |
| grounding | object | ✅ | 백엔드가 DB에서 조립한 정량 근거(예측·발주·탄소·맥락) |

### 5.2 응답 (Response)
```jsonc
200 OK
{
  "answer": "다음 발주주기(7일)에 우유는 66L 발주를 권장합니다. 8일 커버 수요 중앙값은 80L이나 폐기비용이 마진보다 커서 보수적으로 잡았고, 평소(88L) 대비 12L 과발주를 줄여 약 39kgCO₂eq(승용차 8.6km)를 절감합니다.",
  "cacheHit": true,         // 시맨틱 캐시 적중 여부
  "latencyMs": 180,         // sLLM 추론 지연
  "tokens": 142             // 생성 토큰 수(입력+출력 또는 출력)
}
```
| 경로 | 타입 | 설명 |
|---|---|---|
| answer | string | 한국어 2~3문장 설명(근거 수치 인용) |
| cacheHit | bool | 시맨틱 캐싱 적중 여부(저전력 지표) |
| latencyMs | int | 추론 지연(ms) |
| tokens | int | 토큰 수(저전력 지표) |

> `cacheHit/latencyMs/tokens`는 백엔드가 메트릭으로 중계(저전력 입증). **반드시 포함**.

### 5.3 sLLM 행동 규칙 (중요)
- grounding의 수치를 **변경·재계산·창작 금지**. 인용만.
- 근거에 없는 값(예: 다른 품목, 미래 추정)은 답하지 않거나 "근거 없음" 처리.
- 환각 방지를 위해 **수치 자리표시는 grounding에서만** 채운다.

---

## 6. `GET /health`
```jsonc
200 OK
{ "status": "UP", "model": { "forecast": "lgbm_global_v1", "llm": "ko-sLLM-q4" } }
```
- 모델 미로딩이어도 `status:"UP"`이되 `forecast:"baseline_v1"`로 표기 가능.

---

## 7. 에러 응답
HTTP 상태코드 + JSON. 백엔드는 실패 시 `FORECAST_UNAVAILABLE`/`LLM_UNAVAILABLE`로 매핑.
```jsonc
4xx/5xx
{ "error": { "code": "BAD_REQUEST", "message": "coverageDays mismatch (lead+cycle)" } }
```
| 상황 | 상태코드 | code |
|---|---|---|
| 요청 검증 실패 | 400 | `BAD_REQUEST` |
| 모델/추론 실패 | 503 | `INFERENCE_FAILED` |
| 내부 오류 | 500 | `INTERNAL_ERROR` |

---

## 8. 비기능 요구사항 (idea_note §3·§6)
- **저전력 우선:** 수치 연산은 LightGBM(경량), 언어는 소형 양자화 sLLM. 무거운 산정은 호출 자체를 백엔드로 이관.
- **시맨틱 캐싱:** 유사 질의를 임베딩 유사도로 매칭해 재연산 방지. `cacheHit` 보고.
- **계측 노출:** 호출수·토큰·지연·캐시 적중률(`/metrics` 또는 응답 필드). '전부 LLM' 대비 우위 입증 데이터.
- **결정성:** 동일 요청에 가능한 안정적 결과(예측은 seed 고정 권장).
- **서빙(목표):** EC2 + Inferentia(Inf2). Demo는 CPU로 충분.

---

## 9. 모델 버전 규약
| modelVersion | 의미 |
|---|---|
| `lgbm_global_v1` | 학습된 LightGBM 글로벌 모델 |
| `baseline_v1` | ma7 기반 폴백(모델 미준비/실패) |
| `ko-sLLM-q4` 등 | sLLM 모델 식별(health/metrics용) |

---

## 10. 참고 예시 (전체 흐름)
우유 품목, 발주주기 7일·리드타임 1일 → 커버기간 8일 시나리오(`/v1/order-recommendation`)의 요청/응답 전 과정은
[`backend_spec.md` 부록 C](./backend_spec.md)를 참고. (AI는 §3.2 `daily` 8개를 반환하면 백엔드가 합산·발주·탄소까지 산출)

---

## 부록 — AI 레포 ↔ 백엔드 합의 항목(open)
1. `features` 스키마 최종 확정(추가 피처: 행사/신메뉴 플래그 등).
2. `baseline_v1` 분위 배수 규칙 확정.
3. `grounding` 번들 필드 확정(프롬프트 설계와 함께).
4. 인증/시크릿 공유 방식(Demo는 생략 가능).
