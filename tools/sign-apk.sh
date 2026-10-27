#!/usr/bin/env bash
# Runs on a clean runner, after the build job. Never builds PR code with secrets.
set -euo pipefail
set +x
umask 077
unsigned="${1:?unsigned APK required}"
output="${2:?output APK required}"
pin="${3:?trusted certificate fingerprint required}"
: "${SIGNING_PASSWORD:?signing password missing}"
: "${KEYSTORE_B64:?protected keystore missing}"
: "${SIGNING_ALIAS:?alias required}"
tmp="$(mktemp -d "${RUNNER_TEMP:-/tmp}/finance-sign.XXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT
python3 - "$tmp/key.p12" <<'PY'
import base64,os,sys
from pathlib import Path
Path(sys.argv[1]).write_bytes(base64.b64decode(os.environ['KEYSTORE_B64'],validate=True))
PY
unset KEYSTORE_B64
keytool -exportcert -alias "$SIGNING_ALIAS" -keystore "$tmp/key.p12" \
  -storepass:env SIGNING_PASSWORD -file "$tmp/certificate.der" >/dev/null 2>&1
python3 - "$pin" "$tmp/certificate.der" <<'PY'
import hashlib,hmac,re,sys
from pathlib import Path
expected=Path(sys.argv[1]).read_text().strip().lower()
actual=hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()
if not re.fullmatch('[0-9a-f]{64}',expected) or not hmac.compare_digest(expected,actual):
 raise SystemExit('Signing blocked: certificate differs from trusted identity.')
PY
zipalign -P 16 -f 4 "$unsigned" "$tmp/aligned.apk"
apksigner sign --ks "$tmp/key.p12" --ks-key-alias "$SIGNING_ALIAS" \
  --ks-pass env:SIGNING_PASSWORD --key-pass env:SIGNING_PASSWORD \
  --out "$tmp/signed.apk" "$tmp/aligned.apk"
unset SIGNING_PASSWORD
apksigner verify --verbose --print-certs "$tmp/signed.apk"
zipalign -c -P 16 4 "$tmp/signed.apk"
python3 "$(dirname "$0")/verify-distribution.py" "$tmp/signed.apk"
mkdir -p "$(dirname "$output")"
cp "$tmp/signed.apk" "$output"
