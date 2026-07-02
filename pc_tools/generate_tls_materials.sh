#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
TLS_DIR="$SCRIPT_DIR/tls"
ANDROID_ASSET_DIR="$ROOT_DIR/app/src/main/assets/family_tls"
PASSWORD=${FAMILY_TLS_IDENTITY_PASSWORD:-localmanager-family-dev}
DAYS=${FAMILY_TLS_VALID_DAYS:-3650}
FORCE_REGENERATE=0

if [[ ${1:-} == "--force" ]]; then
  FORCE_REGENERATE=1
fi

mkdir -p "$TLS_DIR" "$ANDROID_ASSET_DIR"

CA_KEY="$TLS_DIR/ca_key.pem"
CA_CERT="$TLS_DIR/ca_cert.pem"
CA_SERIAL="$TLS_DIR/ca_cert.srl"
PC_KEY="$TLS_DIR/pc_server_key.pem"
PC_CSR="$TLS_DIR/pc_server.csr"
PC_CERT="$TLS_DIR/pc_server_cert.pem"
ANDROID_KEY="$TLS_DIR/android_dev_key.pem"
ANDROID_KEY_PKCS8="$TLS_DIR/android_dev_key_pkcs8.pem"
ANDROID_CSR="$TLS_DIR/android_dev.csr"
ANDROID_CERT="$TLS_DIR/android_dev_cert.pem"
ANDROID_P12="$TLS_DIR/android_dev_identity.p12"

if [[ "$FORCE_REGENERATE" == "1" ]]; then
  rm -f \
    "$CA_KEY" "$CA_CERT" "$CA_SERIAL" \
    "$PC_KEY" "$PC_CSR" "$PC_CERT" \
    "$ANDROID_KEY" "$ANDROID_KEY_PKCS8" "$ANDROID_CSR" "$ANDROID_CERT" "$ANDROID_P12"
fi

WORK_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

LEAF_EXT="$WORK_DIR/leaf_ext.cnf"
cat > "$LEAF_EXT" <<'EOF'
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
EOF

if [[ ! -f "$CA_KEY" || ! -f "$CA_CERT" ]]; then
  openssl req -x509 -newkey rsa:3072 -sha256 -days "$DAYS" -nodes \
    -keyout "$CA_KEY" \
    -out "$CA_CERT" \
    -subj "/CN=LocalManager Family TLS Root CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -addext "subjectKeyIdentifier=hash"
fi

openssl req -newkey rsa:3072 -sha256 -nodes \
  -keyout "$PC_KEY" \
  -out "$PC_CSR" \
  -subj "/CN=LocalManager PC Service"
openssl x509 -req -sha256 -days "$DAYS" \
  -in "$PC_CSR" \
  -CA "$CA_CERT" \
  -CAkey "$CA_KEY" \
  -CAcreateserial \
  -CAserial "$CA_SERIAL" \
  -out "$PC_CERT" \
  -extfile "$LEAF_EXT"

openssl req -newkey rsa:3072 -sha256 -nodes \
  -keyout "$ANDROID_KEY" \
  -out "$ANDROID_CSR" \
  -subj "/CN=LocalManager Android Dev Identity"
openssl x509 -req -sha256 -days "$DAYS" \
  -in "$ANDROID_CSR" \
  -CA "$CA_CERT" \
  -CAkey "$CA_KEY" \
  -CAcreateserial \
  -CAserial "$CA_SERIAL" \
  -out "$ANDROID_CERT" \
  -extfile "$LEAF_EXT"

openssl pkcs8 -topk8 -nocrypt \
  -in "$ANDROID_KEY" \
  -out "$ANDROID_KEY_PKCS8"

openssl pkcs12 -export \
  -name localmanager-android-dev \
  -inkey "$ANDROID_KEY" \
  -in "$ANDROID_CERT" \
  -certfile "$CA_CERT" \
  -out "$ANDROID_P12" \
  -passout "pass:$PASSWORD"

cp "$CA_CERT" "$ANDROID_ASSET_DIR/ca_cert.pem"
cp "$ANDROID_P12" "$ANDROID_ASSET_DIR/android_dev_identity.p12"
cp "$ANDROID_CERT" "$ANDROID_ASSET_DIR/android_dev_cert.pem"
cp "$ANDROID_KEY_PKCS8" "$ANDROID_ASSET_DIR/android_dev_key_pkcs8.pem"

echo "TLS materials generated under $TLS_DIR"
echo "Android assets updated under $ANDROID_ASSET_DIR"
