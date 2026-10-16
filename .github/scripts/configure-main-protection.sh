#!/usr/bin/env bash
# Admin-only action to protect main. Does not obtain, create or persist tokens.
# Dry-run by default. Requires a GitHub personal/App token authorized for
# Administration (write) on the exact repository when --apply is explicitly set.
set -euo pipefail
repo="drfxai/Maximus-Matrix-OS"
branch="main"
command -v gh >/dev/null || { echo 'Install GitHub CLI first.' >&2; exit 2; }
payload="$(mktemp)"
trap 'rm -f "$payload"' EXIT
cat >"$payload" <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["validate", "required-ci"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true
}
JSON
if [[ "${1:-}" != "--apply" ]]; then
  echo "DRY RUN: GitHub main protection for $repo (no change made)."
  cat "$payload"
  echo 'To apply: set GH_TOKEN or GH_ADMIN_TOKEN with Administration write scope, then execute --apply.'
  exit 0
fi
if [[ "${2:-}" != "--confirm-main-protection" ]]; then
  echo 'Explicit second confirmation required: --apply --confirm-main-protection' >&2
  exit 2
fi
export GH_TOKEN="${GH_ADMIN_TOKEN:-${GH_TOKEN:-}}"
[[ "${#GH_TOKEN}" -ge 20 ]] || { echo 'Admin-capable GitHub token missing.' >&2; exit 2; }
# Read before write and require current branch/contexts to exist.
gh api "repos/$repo/branches/$branch" --jq '.name' | grep -Fxq "$branch"
gh api --method PUT "repos/$repo/branches/$branch/protection" --input "$payload" >/dev/null
gh api "repos/$repo/branches/$branch" --jq '.protected' | grep -Fxq true
echo 'Verified: main is protected. Check that required status contexts are effective in GitHub Settings.'
