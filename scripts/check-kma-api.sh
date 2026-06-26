#!/bin/bash
# 기상청 단기예보 API 연동 점검 스크립트
# 사용법: bash scripts/check-kma-api.sh [SERVICE_KEY] [DATE(YYYYMMDD)]
# 예시:  bash scripts/check-kma-api.sh your-service-key 20260627

set -euo pipefail

SERVICE_KEY="${1:-${KMA_SERVICE_KEY:-}}"
TARGET_DATE="${2:-$(date -d "-1 year" +%Y%m%d 2>/dev/null || python3 -c "from datetime import date; from dateutil.relativedelta import relativedelta; print((date.today()-relativedelta(years=1)).strftime('%Y%m%d'))")}"
BASE_URL="https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0"

# 서울 기준 nx=60, ny=127 (V3 시드와 동일)
NX=60
NY=127
BASE_DATE=$(date -d "${TARGET_DATE:0:4}-${TARGET_DATE:4:2}-${TARGET_DATE:6:2} -1 day" +%Y%m%d 2>/dev/null \
    || python3 -c "from datetime import date,timedelta; d=date(int('${TARGET_DATE:0:4}'),int('${TARGET_DATE:4:2}'),int('${TARGET_DATE:6:2}'))-timedelta(days=1); print(d.strftime('%Y%m%d'))")

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

ok()   { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }
data() { echo -e "${CYAN}       $1${NC}"; }

echo "========================================"
echo "  기상청 단기예보 API 점검"
echo "  예보날짜: $TARGET_DATE"
echo "  기준날짜: ${BASE_DATE} 2300 발표"
echo "  격자좌표: nx=$NX, ny=$NY (서울)"
echo "========================================"
echo ""

# ── 0. 서비스키 확인 ──────────────────────────────────────────────────────────
info "0. 서비스키 확인"
if [ -z "$SERVICE_KEY" ]; then
    fail "서비스키가 없습니다."
    echo "  사용법: bash scripts/check-kma-api.sh <KMA_SERVICE_KEY>"
    echo "  또는 KMA_SERVICE_KEY 환경변수를 설정하세요."
    exit 1
fi
KEY_PREVIEW="${SERVICE_KEY:0:6}...${SERVICE_KEY: -4}"
ok "서비스키 확인: $KEY_PREVIEW (길이: ${#SERVICE_KEY}자)"

# ── 1. API 호출 ───────────────────────────────────────────────────────────────
echo ""
info "1. getVilageFcst API 호출"

API_URL="${BASE_URL}/getVilageFcst"
PARAMS="serviceKey=${SERVICE_KEY}&numOfRows=1000&pageNo=1&dataType=JSON&base_date=${BASE_DATE}&base_time=2300&nx=${NX}&ny=${NY}"

info "요청 URL: ${API_URL}?base_date=${BASE_DATE}&base_time=2300&nx=${NX}&ny=${NY}&..."
echo ""

HTTP_RESP=$(curl -s -w "\n__HTTP_CODE__%{http_code}" \
    --connect-timeout 10 --max-time 30 \
    "${API_URL}?${PARAMS}" 2>/dev/null || echo -e "\n__HTTP_CODE__000")

HTTP_CODE=$(echo "$HTTP_RESP" | grep "__HTTP_CODE__" | sed 's/__HTTP_CODE__//')
BODY=$(echo "$HTTP_RESP" | grep -v "__HTTP_CODE__")

if [ "$HTTP_CODE" = "000" ]; then
    fail "API 서버에 연결할 수 없음 (타임아웃 또는 네트워크 오류)"
    exit 1
fi

ok "HTTP 응답: $HTTP_CODE"

# ── 2. 응답 파싱 ──────────────────────────────────────────────────────────────
echo ""
info "2. 응답 파싱"

RESULT_CODE=$(echo "$BODY" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d['response']['header']['resultCode'])
except Exception as e:
    print('PARSE_ERROR: ' + str(e))
" 2>/dev/null || echo "PARSE_ERROR")

RESULT_MSG=$(echo "$BODY" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d['response']['header']['resultMsg'])
except:
    print('unknown')
" 2>/dev/null || echo "unknown")

if [ "$RESULT_CODE" = "00" ]; then
    ok "resultCode: $RESULT_CODE ($RESULT_MSG)"
else
    fail "resultCode: $RESULT_CODE ($RESULT_MSG)"
    echo ""
    echo "  에러 코드 설명:"
    case "$RESULT_CODE" in
        "01") echo "  APPLICATION_ERROR — 서비스키 또는 파라미터 오류" ;;
        "02") echo "  DB_ERROR — 공공데이터포털 DB 오류" ;;
        "03") echo "  NODATA_ERROR — 해당 날짜 데이터 없음 (너무 먼 미래/과거)" ;;
        "04") echo "  HTTP_ERROR" ;;
        "05") echo "  SERVICETIMEOUT_ERROR" ;;
        "10") echo "  INVALID_REQUEST_PARAMETER_ERROR — 필수 파라미터 누락" ;;
        "11") echo "  NO_MANDATORY_REQUEST_PARAMETERS_ERROR" ;;
        "12") echo "  NO_OPENAPI_SERVICE_ERROR — 서비스키 미등록 또는 권한 없음" ;;
        "20") echo "  SERVICE_ACCESS_DENIED_ERROR — IP 차단 또는 호출 한도 초과" ;;
        "22") echo "  LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR — 일일 할당량 초과" ;;
        "30") echo "  SERVICE_KEY_IS_NOT_REGISTERED_ERROR — 서비스키 미등록" ;;
        "31") echo "  DEADLINE_HAS_EXPIRED_ERROR — 서비스키 기간 만료" ;;
        "32") echo "  UNREGISTERED_IP_ERROR — 비허가 IP" ;;
        "PARSE_ERROR") echo "  JSON 파싱 실패 — 응답이 JSON이 아닐 수 있음 (XML 오류 응답 가능성)" ;;
        *) echo "  알 수 없는 코드" ;;
    esac
    echo ""
    echo "  원본 응답 (앞 500자):"
    echo "$BODY" | head -c 500
    exit 1
fi

# ── 3. 예보 데이터 항목 추출 ──────────────────────────────────────────────────
echo ""
info "3. 예보 데이터 확인 (날짜: $TARGET_DATE)"

BODY_FILE=$(mktemp)
echo "$BODY" > "$BODY_FILE"

python3 - "$BODY_FILE" "$TARGET_DATE" << 'PYEOF'
import sys, json

GREEN = '\033[0;32m'
RED   = '\033[0;31m'
NC    = '\033[0m'

body_file = sys.argv[1]
target    = sys.argv[2]

with open(body_file) as f:
    raw = f.read()

try:
    d     = json.loads(raw)
    items = d['response']['body']['items']['item']
except Exception as e:
    print(f"  파싱 오류: {e}")
    sys.exit(1)

day_items = [i for i in items if i.get('fcstDate') == target]
print(f"  전체 항목 수: {len(items)}")
print(f"  {target} 해당 항목 수: {len(day_items)}")
print()

if not day_items:
    dates = sorted(set(i.get('fcstDate','') for i in items))[:5]
    print(f"  경고: {target} 날짜의 예보 데이터가 없습니다.")
    print(f"  사용 가능한 날짜: {dates}")
    sys.exit(1)

categories = ['TMX', 'TMN', 'SKY', 'POP', 'PCP']
found = {}
for cat in categories:
    vals = [i['fcstValue'] for i in day_items if i.get('category') == cat]
    found[cat] = vals[0] if vals else None

print("  핵심 예보값:")
labels = {
    'TMX': '최고기온',
    'TMN': '최저기온',
    'SKY': '하늘상태(1맑음/3구름많음/4흐림)',
    'POP': '강수확률(%)',
    'PCP': '강수량',
}
all_ok = True
for cat, label in labels.items():
    val = found.get(cat)
    if val is not None:
        print(f"  {GREEN}[PASS]{NC} {cat} ({label}): {val}")
    else:
        print(f"  {RED}[FAIL]{NC} {cat} ({label}): 없음")
        all_ok = False

print()
print("  날씨 해석 (백엔드 로직 기준):")
try:
    pcp_val = found.get('PCP') or '강수없음'
    pcp_num = float(''.join(c for c in pcp_val if c.isdigit() or c == '.') or '0')
    sky     = int(found.get('SKY') or 0)
    tmx     = float(found.get('TMX') or 0)
    tmn     = float(found.get('TMN') or 0)
    avg     = (tmx + tmn) / 2
    wx      = '비' if pcp_num > 0 else ('흐림' if sky >= 3 else '맑음')
    print(f"    날씨: {wx}")
    print(f"    평균기온: {avg:.1f}°C  (최고 {tmx}°C / 최저 {tmn}°C)")
    print(f"    강수량: {pcp_val}  /  강수확률: {found.get('POP', '-')}%")
except Exception as e:
    print(f"    해석 오류: {e}")

print()
if all_ok:
    print(f"  {GREEN}모든 핵심 항목 확인 완료{NC}")
else:
    print(f"  {RED}일부 항목 누락{NC}")
    sys.exit(1)
PYEOF
EXIT_CODE=$?
rm -f "$BODY_FILE"
EXIT_CODE=$?

echo ""
echo "========================================"
if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}기상청 API 연동 정상${NC}"
else
    echo -e "${RED}기상청 API 점검 실패 — 위 로그를 확인하세요${NC}"
fi
echo "========================================"
exit $EXIT_CODE
