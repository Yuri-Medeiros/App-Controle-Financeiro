#!/usr/bin/env bash
# Signs only with the explicitly configured release key. Never generates a key.
set -euo pipefail
set +x
umask 077
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${MEU_FINANCEIRO_SIGNING_PASSWORD:-}" ]]; then
  echo 'Signing blocked: personal variable MEU_FINANCEIRO_SIGNING_PASSWORD is missing.' >&2
  exit 2
fi
release_keystore="${MEU_FINANCEIRO_SIGNING_KEYSTORE:-$FINANCE_CLOUD_ROOT/signing/meu-financeiro-release.p12}"
if [[ ! -f "$release_keystore" ]]; then
  echo 'Signing blocked: MEU_FINANCEIRO_SIGNING_KEYSTORE must identify the protected release keystore.' >&2
  exit 2
fi
certificate_pin="$project_dir/signing/release-certificate.sha256"
if [[ ! -f "$certificate_pin" ]]; then
  echo 'Signing blocked: the approved public release certificate fingerprint is not recorded.' >&2
  exit 2
fi
unsigned="$project_dir/app/build/outputs/apk/release/app-release-unsigned.apk"
test -f "$unsigned" || { echo 'Build the release APK first.' >&2; exit 2; }
signing_tmp="$(mktemp -d "$FINANCE_CLOUD_ROOT/signing-run.XXXXXX")"
trap 'rm -rf -- "$signing_tmp"' EXIT
keytool -exportcert -keystore "$release_keystore" \
  -alias meu-financeiro -storepass:env MEU_FINANCEIRO_SIGNING_PASSWORD \
  -file "$signing_tmp/certificate.der" >/dev/null 2>&1
python - "$certificate_pin" "$signing_tmp/certificate.der" <<'PY'
import hashlib,hmac,re,sys
from pathlib import Path
expected=Path(sys.argv[1]).read_text().strip().lower().replace(':','')
if not re.fullmatch('[0-9a-f]{64}',expected):
 raise SystemExit('Invalid public certificate fingerprint.')
actual=hashlib.sha256(Path(sys.argv[2]).read_bytes()).hexdigest()
if not hmac.compare_digest(expected,actual):
 raise SystemExit('Signing blocked: certificate does not match the approved release identity.')
PY
zipalign -P 16 -f 4 "$unsigned" "$signing_tmp/aligned.apk"
apksigner sign --ks "$release_keystore" --ks-key-alias meu-financeiro \
  --ks-pass env:MEU_FINANCEIRO_SIGNING_PASSWORD --key-pass env:MEU_FINANCEIRO_SIGNING_PASSWORD \
  --out "$signing_tmp/signed.apk" "$signing_tmp/aligned.apk"
apksigner verify --verbose --print-certs "$signing_tmp/signed.apk"
zipalign -c -P 16 4 "$signing_tmp/signed.apk"
mkdir -p "$project_dir/build/distributions"
cp "$signing_tmp/signed.apk" "$project_dir/build/distributions/Meu-Financeiro-0.1.0.apk"
sha256sum "$project_dir/build/distributions/Meu-Financeiro-0.1.0.apk"
