#!/bin/bash
set -e

DOMAIN="34-229-22-15.sslip.io"
EMAIL="${CERTBOT_EMAIL:-admin@example.com}"

echo "=== [1/4] 더미 인증서 생성 (nginx 최초 기동용) ==="
docker compose run --rm --entrypoint sh certbot -c "
  mkdir -p /etc/letsencrypt/live/$DOMAIN &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout /etc/letsencrypt/live/$DOMAIN/privkey.pem \
    -out    /etc/letsencrypt/live/$DOMAIN/fullchain.pem \
    -subj   '/CN=localhost' 2>/dev/null
"

echo "=== [2/4] nginx + backend 기동 ==="
docker compose up -d backend nginx

echo "=== [3/4] Let's Encrypt 인증서 발급 ==="
docker compose run --rm --entrypoint sh certbot -c "
  certbot certonly --webroot \
    --webroot-path /var/www/certbot \
    --email $EMAIL \
    --agree-tos --no-eff-email \
    -d $DOMAIN
"

echo "=== [4/4] nginx 설정 리로드 (실제 인증서 적용) ==="
docker compose exec nginx nginx -s reload

echo ""
echo "완료! https://$DOMAIN 으로 접근 가능합니다."
