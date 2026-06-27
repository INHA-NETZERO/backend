#!/bin/bash
# AI 서버 연동 상태 점검 스크립트
# 사용법: bash scripts/check-ai-server.sh [AI_SERVER_URL]
# 예시:  bash scripts/check-ai-server.sh http://localhost:8000

set -euo pipefail

AI_URL="${1:-${AI_SERVER_URL:-http://localhost:8000}}"
PASS=0
FAIL=0

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

ok()      { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
fail()    { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }
info()    { echo -e "${YELLOW}[INFO]${NC} $1"; }
json_pp() { python3 -c "import sys,json; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=4))" 2>/dev/null || cat; }

echo "========================================"
echo "  AI 서버 연동 점검"
echo "  대상: $AI_URL"
echo "========================================"
echo ""

# ── 0. 기본 연결 ──────────────────────────────────────────────────────────────
info "0. 기본 연결 확인"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$AI_URL" 2>/dev/null || echo "000")
if [ "$HTTP_CODE" != "000" ]; then
    ok "서버 응답 확인 (HTTP $HTTP_CODE)"
else
    fail "서버에 연결할 수 없음 ($AI_URL)"
    echo ""
    echo "AI_SERVER_URL 환경변수 또는 인자를 확인하세요."
    exit 1
fi

# ── 1. /v1/forecast 엔드포인트 ────────────────────────────────────────────────
echo ""
info "1. /v1/forecast (수요예측) 점검"

FORECAST_PAYLOAD='{
  "storeId": 1,
  "targetDate": "2026-06-28",
  "salesHistory": {
    "presignedUrls": ["https://zerowave.s3.amazonaws.com/sales-2025-05.csv?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test"],
    "format": "sales_csv_v1"
  },
  "weather": {
    "forecastDate": "2026-06-28",
    "avgTemp": 21.2,
    "precipitationMm": 12.0,
    "precipitationProb": 80,
    "skyCode": 4
  },
  "rows": [
    {
      "itemId": 101,
      "features": {"dayOfWeek": 6, "isHoliday": false, "ma7": 9.4, "trend": -0.3}
    }
  ]
}'

FORECAST_RESP=$(curl -s -X POST "$AI_URL/v1/forecast" \
    -H "Content-Type: application/json" \
    -d "$FORECAST_PAYLOAD" \
    --connect-timeout 10 \
    --max-time 30 \
    -w "\n__HTTP_CODE__%{http_code}" 2>/dev/null || echo -e "\n__HTTP_CODE__000")

FORECAST_HTTP=$(echo "$FORECAST_RESP" | grep "__HTTP_CODE__" | sed 's/__HTTP_CODE__//')
FORECAST_BODY=$(echo "$FORECAST_RESP" | grep -v "__HTTP_CODE__")

echo "  응답 (HTTP $FORECAST_HTTP):"
echo "$FORECAST_BODY" | json_pp
echo ""
if [ "$FORECAST_HTTP" = "200" ]; then
    ok "/v1/forecast → HTTP 200"
    if echo "$FORECAST_BODY" | grep -q "modelVersion\|predictions"; then
        ok "응답 구조 확인 (modelVersion / predictions 포함)"
    else
        fail "응답에 modelVersion 또는 predictions 필드 없음"
    fi
elif [ "$FORECAST_HTTP" = "000" ]; then
    fail "/v1/forecast 타임아웃 또는 연결 실패"
else
    fail "/v1/forecast → HTTP $FORECAST_HTTP"
fi

# ── 2. /v1/order-recommendation 엔드포인트 ────────────────────────────────────
echo ""
info "2. /v1/order-recommendation (발주 추천) 점검"

ORDER_PAYLOAD='{
  "storeId": 1,
  "targetDate": "2026-06-27",
  "salesHistory": {
    "presignedUrls": ["https://zerowave.s3.ap-northeast-2.amazonaws.com/sales/store1/sales-2026-05.csv?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test"],
    "format": "sales_csv_v1"
  },
  "coverage": {
    "leadTimeDays": 1,
    "orderCycleDays": 7,
    "coverageDays": 8
  },
  "weather": [
    {
      "forecastDate": "2026-06-28",
      "avgTemp": 21.2,
      "precipitationMm": 12.0,
      "precipitationProb": 80,
      "skyCode": 4
    }
  ],
  "rows": [
    {
      "itemId": 101,
      "orderCycleDays": 7,
      "leadTimeDays": 1,
      "features": {"dayOfWeek": 6, "isHoliday": false, "ma7": 9.4, "trend": -0.3}
    }
  ]
}'

ORDER_RESP=$(curl -s -X POST "$AI_URL/v1/order-recommendation" \
    -H "Content-Type: application/json" \
    -d "$ORDER_PAYLOAD" \
    --connect-timeout 10 \
    --max-time 30 \
    -w "\n__HTTP_CODE__%{http_code}" 2>/dev/null || echo -e "\n__HTTP_CODE__000")

ORDER_HTTP=$(echo "$ORDER_RESP" | grep "__HTTP_CODE__" | sed 's/__HTTP_CODE__//')
ORDER_BODY=$(echo "$ORDER_RESP" | grep -v "__HTTP_CODE__")

echo "  응답 (HTTP $ORDER_HTTP):"
echo "$ORDER_BODY" | json_pp
echo ""
if [ "$ORDER_HTTP" = "200" ]; then
    ok "/v1/order-recommendation → HTTP 200"
    if echo "$ORDER_BODY" | grep -q "modelVersion"; then
        ok "응답 구조 확인 (modelVersion 포함)"
    else
        fail "응답에 modelVersion 필드 없음"
    fi
    if echo "$ORDER_BODY" | grep -q "daily"; then
        ok "응답 구조 확인 (predictions[].daily 포함)"
    else
        fail "응답에 predictions[].daily 필드 없음"
    fi
elif [ "$ORDER_HTTP" = "000" ]; then
    fail "/v1/order-recommendation 타임아웃 또는 연결 실패"
else
    fail "/v1/order-recommendation → HTTP $ORDER_HTTP"
fi

# ── 3. /v1/generate (LLM 챗) 점검 ────────────────────────────────────────────
echo ""
info "3. /v1/generate (LLM 챗) 점검"

GENERATE_PAYLOAD='{
  "question": "오늘 우유 발주 수량이 왜 이렇게 많나요?",
  "locale": "ko",
  "grounding": {
    "item": { "itemId": 101, "itemName": "우유", "unit": "L" },
    "coverage": { "leadTimeDays": 1, "orderCycleDays": 7, "coverageDays": 8 },
    "forecast": { "p10": 60, "p50": 80, "p90": 108 },
    "recommendation": {
      "recommendedQuantity": 66,
      "optimalStockQuantity": 76.1,
      "onHand": 12,
      "baselineQuantity": 88,
      "criticalRatio": 0.421
    },
    "carbon": {
      "wasteAvoidedKg": 12.31,
      "guaranteedSavingKg": 2.46,
      "potentialSavingKg": 39.4,
      "carEquivalentKm": 8.6
    },
    "context": { "date": "2026-06-27", "storeId": 1 }
  }
}'

GENERATE_RESP=$(curl -s -X POST "$AI_URL/v1/generate" \
    -H "Content-Type: application/json" \
    -d "$GENERATE_PAYLOAD" \
    --connect-timeout 10 \
    --max-time 60 \
    -w "\n__HTTP_CODE__%{http_code}" 2>/dev/null || echo -e "\n__HTTP_CODE__000")

GENERATE_HTTP=$(echo "$GENERATE_RESP" | grep "__HTTP_CODE__" | sed 's/__HTTP_CODE__//')
GENERATE_BODY=$(echo "$GENERATE_RESP" | grep -v "__HTTP_CODE__")

echo "  응답 (HTTP $GENERATE_HTTP):"
echo "$GENERATE_BODY" | json_pp
echo ""
if [ "$GENERATE_HTTP" = "200" ]; then
    ok "/v1/generate → HTTP 200"
    if echo "$GENERATE_BODY" | grep -q "answer"; then
        ok "응답 구조 확인 (answer 포함)"
    else
        fail "응답에 answer 필드 없음"
    fi
elif [ "$GENERATE_HTTP" = "000" ]; then
    fail "/v1/generate 타임아웃 또는 연결 실패"
else
    fail "/v1/generate → HTTP $GENERATE_HTTP"
fi

# ── 결과 요약 ─────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo "  결과: PASS $PASS / FAIL $FAIL"
echo "========================================"

if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN}AI 서버 연동 정상${NC}"
    exit 0
else
    echo -e "${RED}일부 점검 실패 — 위 로그를 확인하세요${NC}"
    exit 1
fi
