#!/usr/bin/env bash
# Run inside the repository, or pass its path. Infrastructure only.
set -euo pipefail
repo_dir="$(git -C "${1:-.}" rev-parse --show-toplevel)"
git -C "$repo_dir" remote get-url origin >/dev/null
# Keep the inherited proxy and TLS validation. Never embed credentials.
GIT_TERMINAL_PROMPT=0 git -C "$repo_dir" \
  -c http.connectTimeout=20 -c http.lowSpeedLimit=1 -c http.lowSpeedTime=30 \
  fetch origin refs/heads/work:refs/remotes/origin/work
remote_sha="$(git -C "$repo_dir" rev-parse refs/remotes/origin/work)"
if [[ -n "$(git -C "$repo_dir" status --porcelain)" ]]; then
  echo 'Remote fetched. Local changes preserved; stopping before checkout/merge.' >&2
  exit 2
fi
if git -C "$repo_dir" rev-parse --verify HEAD >/dev/null 2>&1; then
  [[ "$(git -C "$repo_dir" branch --show-current)" == work ]] || {
    echo 'Remote fetched. Current branch is not work; no branch changed.' >&2; exit 2;
  }
  git -C "$repo_dir" merge --ff-only refs/remotes/origin/work
else
  # A freshly prepared environment may contain an unborn work branch.
  git -C "$repo_dir" switch -c work --track origin/work
fi
git -C "$repo_dir" cat-file -e "$remote_sha:DECISOES.md"
test -f "$repo_dir/DECISOES.md"
echo "Repository synchronized; remote work: $remote_sha"
