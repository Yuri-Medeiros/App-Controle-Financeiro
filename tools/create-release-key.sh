#!/usr/bin/env bash
# Run once in the cloud with the real personal password already injected.
set -euo pipefail
set +x
umask 077
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${MEU_FINANCEIRO_SIGNING_PASSWORD:-}" ]]; then
  echo 'Key creation blocked: personal signing password is missing.' >&2
  exit 2
fi
key_dir="$FINANCE_CLOUD_ROOT/signing"
key_file="$key_dir/meu-financeiro-release.p12"
certificate_pin="$project_dir/signing/release-certificate.sha256"
if [[ -e "$key_file" || -e "$certificate_pin" ]]; then
  echo 'Key creation blocked: an existing identity must be recovered, not replaced.' >&2
  exit 2
fi
mkdir -p "$key_dir"
chmod 700 "$key_dir"
creation_tmp="$(mktemp -d "$key_dir/create.XXXXXX")"
trap 'rm -rf -- "$creation_tmp"' EXIT
keytool -genkeypair -storetype PKCS12 -alias meu-financeiro \
  -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 \
  -dname 'CN=Meu Financeiro' -keystore "$creation_tmp/key.p12" \
  -storepass:env MEU_FINANCEIRO_SIGNING_PASSWORD \
  -keypass:env MEU_FINANCEIRO_SIGNING_PASSWORD >/dev/null 2>&1
keytool -exportcert -alias meu-financeiro -keystore "$creation_tmp/key.p12" \
  -storepass:env MEU_FINANCEIRO_SIGNING_PASSWORD \
  -file "$creation_tmp/certificate.der" >/dev/null 2>&1
sha256sum "$creation_tmp/certificate.der" | cut -d' ' -f1 > "$creation_tmp/certificate.sha256"
mv "$creation_tmp/key.p12" "$key_file"
mkdir -p "$project_dir/signing"
cp "$creation_tmp/certificate.sha256" "$certificate_pin"
echo "Protected release keystore created: $key_file"
echo 'Back it up to the private Google Drive and verify recovery before distributing an APK.'
echo 'Only signing/release-certificate.sha256 is public and may be committed.'
