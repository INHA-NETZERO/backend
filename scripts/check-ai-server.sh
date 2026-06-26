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

ok()   { echo -e "${GREEN}[PASS]${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; FAIL=$((FAIL + 1)); }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

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
  "targetDate": "2026-06-27",
  "salesPresignedUrls": [],
  "coverage": {"startDate": "2026-01-01", "endDate": "2026-06-26"},
  "weather": [],
  "rows": [
    {
      "itemCode": "R01",
      "itemName": "우유",
      "category": "원재료",
      "features": {"dayOfWeek": 5, "isHoliday": false, "trend": 0.05}
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

if [ "$FORECAST_HTTP" = "200" ]; then
    ok "/v1/forecast → HTTP 200"
    # modelVersion 필드 확인
    if echo "$FORECAST_BODY" | grep -q "modelVersion\|predictions"; then
        ok "응답 구조 확인 (modelVersion / predictions 포함)"
    else
        fail "응답에 modelVersion 또는 predictions 필드 없음"
        echo "  응답: $FORECAST_BODY"
    fi
elif [ "$FORECAST_HTTP" = "000" ]; then
    fail "/v1/forecast 타임아웃 또는 연결 실패"
else
    fail "/v1/forecast → HTTP $FORECAST_HTTP"
    echo "  응답: $FORECAST_BODY"
fi

# ── 2. /v1/order-recommendation 엔드포인트 ────────────────────────────────────
echo ""
info "2. /v1/order-recommendation (발주 추천) 점검"

ORDER_RESP=$(curl -s -X POST "$AI_URL/v1/order-recommendation" \
    -H "Content-Type: application/json" \
    -d "$FORECAST_PAYLOAD" \
    --connect-timeout 10 \
    --max-time 30 \
    -w "\n__HTTP_CODE__%{http_code}" 2>/dev/null || echo -e "\n__HTTP_CODE__000")

ORDER_HTTP=$(echo "$ORDER_RESP" | grep "__HTTP_CODE__" | sed 's/__HTTP_CODE__//')
ORDER_BODY=$(echo "$ORDER_RESP" | grep -v "__HTTP_CODE__")

if [ "$ORDER_HTTP" = "200" ]; then
    ok "/v1/order-recommendation → HTTP 200"
    if echo "$ORDER_BODY" | grep -q "modelVersion\|predictions"; then
        ok "응답 구조 확인 (modelVersion / predictions 포함)"
    else
        fail "응답에 modelVersion 또는 predictions 필드 없음"
        echo "  응답: $ORDER_BODY"
    fi
elif [ "$ORDER_HTTP" = "000" ]; then
    fail "/v1/order-recommendation 타임아웃 또는 연결 실패"
else
    fail "/v1/order-recommendation → HTTP $ORDER_HTTP"
    echo "  응답: $ORDER_BODY"
fi

# ── 3. /v1/generate (LLM 챗) 점검 ────────────────────────────────────────────
echo ""
info "3. /v1/generate (LLM 챗) 점검"

GENERATE_PAYLOAD='{
  "question": "오늘 우유 발주 수량이 왜 이렇게 많나요?",
  "locale": "ko",
  "grounding": {
    "storeId": 1,
    "date": "2026-06-27",
    "itemName": "우유",
    "recommendedQty": 50,
    "currentStock": 10,
    "forecastDemand": 45
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

if [ "$GENERATE_HTTP" = "200" ]; then
    ok "/v1/generate → HTTP 200"
    if echo "$GENERATE_BODY" | grep -q "answer"; then
        ok "응답 구조 확인 (answer 포함)"
        ANSWER=$(echo "$GENERATE_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin).get('answer','')[:80])" 2>/dev/null || echo "(파싱 실패)")
        info "answer 미리보기: $ANSWER..."
    else
        fail "응답에 answer 필드 없음"
        echo "  응답: $GENERATE_BODY"
    fi
elif [ "$GENERATE_HTTP" = "000" ]; then
    fail "/v1/generate 타임아웃 또는 연결 실패"
else
    fail "/v1/generate → HTTP $GENERATE_HTTP"
    echo "  응답: $GENERATE_BODY"
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
