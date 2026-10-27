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
version="$(python3 - "$project_dir/version.properties" <<'PYVERSION'
from pathlib import Path
import re,sys
s=Path(sys.argv[1]).read_text()
m=re.fullmatch(r'versionName=([0-9]+\.[0-9]+\.[0-9]+)\s*', s)
if not m: raise SystemExit('Invalid version.properties')
print(m[1])
PYVERSION
)"
export KEYSTORE_B64="$(base64 -w0 "$release_keystore")"
export SIGNING_PASSWORD="$MEU_FINANCEIRO_SIGNING_PASSWORD"
export SIGNING_ALIAS=meu-financeiro
export APK_PACKAGE=br.com.yurimedeiros.meufinanceiro
export APK_VERSION="$version"
export APK_CODE="${MEU_FINANCEIRO_VERSION_CODE:-1}"
output="$project_dir/build/distributions/Meu-Financeiro-$version.apk"
bash "$project_dir/tools/sign-apk.sh" "$unsigned" "$output" "$certificate_pin"
unset KEYSTORE_B64 SIGNING_PASSWORD
sha256sum "$output"
