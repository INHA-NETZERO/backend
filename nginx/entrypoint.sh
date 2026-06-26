#!/bin/sh
set -e

CERT_DIR="/etc/ssl/nginx"
mkdir -p "$CERT_DIR"

if [ ! -f "$CERT_DIR/self.crt" ]; then
    echo "[nginx] self-signed 인증서 생성 중..."
    openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
        -keyout "$CERT_DIR/self.key" \
        -out    "$CERT_DIR/self.crt" \
        -subj   "/CN=34-229-22-15.sslip.io" \
        2>/dev/null
    echo "[nginx] 인증서 생성 완료"
fi

exec nginx -g 'daemon off;'
