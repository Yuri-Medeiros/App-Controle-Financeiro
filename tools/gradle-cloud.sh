#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
# Java clients must use the same inherited proxy and CA trust as the executor.
mapfile -t proxy_args < <(python - <<'PY'
import os,urllib.parse
u=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',os.environ.get('HTTP_PROXY','')))
if u.hostname:
 for scheme in ('https','http'):
  print(f'-D{scheme}.proxyHost={u.hostname}')
  print(f'-D{scheme}.proxyPort={u.port or 8080}')
PY
)
trust_args=()
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  trust_args+=("-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
fi
exec gradle --no-daemon "${proxy_args[@]}" "${trust_args[@]}" "$@"
