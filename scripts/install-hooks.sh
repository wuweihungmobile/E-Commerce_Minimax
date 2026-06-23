#!/usr/bin/env sh
# =============================================
# AISDLC v0.09: Hook 安裝腳本
# 安裝所有 Git Hooks（pre-commit + commit-msg + pre-push）
# 使用方式：./scripts/install-hooks.sh
# 環境變數：
#   VERIFY_HOOKS=1  僅驗證 Hook 是否正確安裝
# =============================================

set -e

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

# 跨平台 sha256：優先 sha256sum（Linux），fallback shasum -a 256（macOS）
calc_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        shasum -a 256 "$1" | cut -d' ' -f1
    fi
}

# 🔴 CRITICAL: 清理可能的衝突 .husky 目錄
# .husky/ 和 .git/hooks/ 同時存在會造成 Hook 執行混亂
if [ -d ".husky" ]; then
    echo "⚠️  發現 .husky/ 目錄，可能與 .git/hooks/ 衝突"
    echo "🔄 清理 .husky/ 目錄..."
    rm -rf .husky/
    echo "  ✅ 已移除 .husky/"
fi

VERIFY_HOOKS="${VERIFY_HOOKS:-0}"

if [ "$VERIFY_HOOKS" = "1" ]; then
    echo "🔍 驗證 Hook 安裝..."
    echo "=========================================="

    # 計算目前 Hook 的雜湊值用於日後比對
    if [ -f ".git/hooks/pre-commit" ]; then
        echo "📝 pre-commit hash: $(calc_sha256 .git/hooks/pre-commit)"
    fi
    if [ -f ".git/hooks/commit-msg" ]; then
        echo "📋 commit-msg hash: $(calc_sha256 .git/hooks/commit-msg)"
    fi
    if [ -f ".git/hooks/pre-push" ]; then
        echo "📤 pre-push hash: $(calc_sha256 .git/hooks/pre-push)"
    fi
    exit 0
fi

echo "🔧 安裝 Git Hooks..."
echo "=========================================="

# 🧹 清理舊備份檔案（防止 commit-msg.bak 等舊檔案殘留）
echo "🧹 [0/5] 清理舊的備份檔案..."
rm -f .git/hooks/*.bak
rm -f .git/hooks/*.old
rm -f .git/hooks/*~
echo "  ✅ 已清理"

# 1. 安裝 root pre-commit hook
echo "📝 [1/5] 安裝 root pre-commit hook..."
mkdir -p .git/hooks
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
echo "  ✅ .git/hooks/pre-commit"

# 2. 安裝 pre-push hook
echo "📤 [2/5] 安裝 pre-push hook..."
cp scripts/hooks/pre-push .git/hooks/pre-push
chmod +x .git/hooks/pre-push
echo "  ✅ .git/hooks/pre-push"

# 3. 安裝 commit-msg hook（基本訊息格式檢查；注意：--no-verify 同時跳過 pre-commit 和 commit-msg，唯一 CI 守門員是 pre-push）
echo "📋 [3/5] 安裝 commit-msg hook..."
cp scripts/hooks/commit-msg .git/hooks/commit-msg
chmod +x .git/hooks/commit-msg
echo "  ✅ .git/hooks/commit-msg"

# 4. 確保 backend/hooks/pre-commit 可執行
echo "🔨 [4/5] 確保 backend pre-commit hook 可執行..."
chmod +x backend/hooks/pre-commit
echo "  ✅ backend/hooks/pre-commit"

# 5. 驗證 Hook 是否正確安裝
echo "🔍 [5/5] 驗證 Hook 安裝..."
if [ -f ".git/hooks/pre-commit" ] && [ -x ".git/hooks/pre-commit" ]; then
    echo "  ✅ pre-commit 已正確安裝並可執行"
else
    echo "  ❌ pre-commit 安裝失敗！"
    exit 1
fi

if [ -f ".git/hooks/pre-push" ] && [ -x ".git/hooks/pre-push" ]; then
    echo "  ✅ pre-push 已正確安裝並可執行"
else
    echo "  ❌ pre-push 安裝失敗！"
    exit 1
fi

if [ -f ".git/hooks/commit-msg" ] && [ -x ".git/hooks/commit-msg" ]; then
    echo "  ✅ commit-msg 已正確安裝並可執行"
else
    echo "  ❌ commit-msg 安裝失敗！"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 所有 Hooks 安裝完成！"
echo ""
echo "📋 Hook 功能說明（v3，2026-06-23）："
echo "  📝 pre-commit: 快速檢查（lint + compile + 核心測試 + secret 掃描，約 1-2 分鐘）"
echo "  📋 commit-msg: 基本訊息檢查（防止空訊息）"
echo "  📤 pre-push:   ⭐ 唯一嚴格門：推送前批次跑一次完整 act CI（依變動範圍只跑相關 job）"
echo ""
echo "🔐 安全機制："
echo "  ✅ commit 快速、迭代順暢（不再每次 commit 重跑 act）"
echo "  ✅ push 前必跑 act，沒通過就擋下（「沒驗證就上去 GIT」保證不變）"
echo "  ✅ 10 分鐘內 + tree-hash 相符的 make validate-all 記錄可直接放行"
echo "  ✅ 檢查 .ci-validation-data 是否被錯誤 commit"
echo ""
echo "⚠️  禁止使用 --no-verify："
echo "  git commit --no-verify  # 🔴 繞過 pre-commit + commit-msg（commit-time hooks），但 pre-push 仍會執行"
echo "  git push --no-verify    # 🔴 已封鎖，繞過 pre-push（唯一 CI 守門員），會導致遠端 CI 失敗"
echo ""
echo "🧪 測試 hooks："
echo "  echo 'test' >> test.txt && git add test.txt && git commit -m 'test'"
echo ""
echo "🔍 驗證 Hook 安裝："
echo "  VERIFY_HOOKS=1 ./scripts/install-hooks.sh"
echo ""
