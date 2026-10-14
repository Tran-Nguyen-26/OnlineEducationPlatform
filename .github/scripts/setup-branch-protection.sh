#!/usr/bin/env bash
# ============================================================
# Bật Branch Protection cho main và dev qua GitHub CLI (gh)
# ============================================================
# Yêu cầu: gh CLI đã login (gh auth login)
# Cách dùng:
#   chmod +x .github/scripts/setup-branch-protection.sh
#   REPO="your-org/OnlineEducationPlatform" bash .github/scripts/setup-branch-protection.sh
# ============================================================

set -euo pipefail

REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
BRANCHES=("main" "dev")

for BRANCH in "${BRANCHES[@]}"; do
  echo "⚙️  Đang cấu hình branch protection cho: $BRANCH ..."

  gh api \
    --method PUT \
    -H "Accept: application/vnd.github+json" \
    "/repos/${REPO}/branches/${BRANCH}/protection" \
    --input - <<EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "Build & Verify Backend",
      "Lint, Type-check & Build Frontend"
    ]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true
}
EOF

  echo "✅  Branch '$BRANCH' đã được bảo vệ!"
done

echo ""
echo "🎉 Xong! Kiểm tra tại: https://github.com/${REPO}/settings/branches"
