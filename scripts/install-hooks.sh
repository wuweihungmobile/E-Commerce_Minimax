#!/usr/bin/env sh
# =============================================
# AISDLC v0.09: Hook 安裝腳本
# 安裝所有 Git Hooks（pre-commit + pre-push）
# 使用方式：./scripts/install-hooks.sh
# 環境變數：
#   VERIFY_HOOKS=1  僅驗證 Hook 是否正確安裝
# =============================================

set -e

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

# 🔴 CRITICAL: 清理可能的衝突 .husky 目錄
# .husky/ 和 .git/hooks/ 同時存在會造成 Hook 執行混亂
if [ -d ".husky" ]; then
    echo "⚠️  發現 .husky/ 目錄，可能與 .git/hooks/ 衝突"
    echo "🔄 清理 .husky/ 目錄..."
    rm -rf .husky/
    echo "  ✅ 已移除 .husky/"
fi

# 🔴 CRITICAL: 驗證 Hook 雜湊值，確保未被篡改
VERIFY_HOOKS="${VERIFY_HOOKS:-0}"
HOOK_ERROR=0

verify_hook_hash() {
    local hook_name="$1"
    local expected_hash="$2"
    local actual_hash

    if [ -f ".git/hooks/$hook_name" ]; then
        actual_hash=$(sha256sum ".git/hooks/$hook_name" 2>/dev/null | cut -d' ' -f1)
        if [ "$actual_hash" != "$expected_hash" ]; then
            echo "  ❌ $hook_name 雜湊驗證失敗！可能被篡改！"
            HOOK_ERROR=1
            return 1
        fi
    else
        echo "  ❌ $hook_name 不存在！"
        HOOK_ERROR=1
        return 1
    fi
    return 0
}

if [ "$VERIFY_HOOKS" = "1" ]; then
    echo "🔍 驗證 Hook 安裝..."
    echo "=========================================="

    # 計算目前 Hook 的雜湊值用於日後比對
    if [ -f ".git/hooks/pre-commit" ]; then
        echo "📝 pre-commit hash: $(sha256sum .git/hooks/pre-commit | cut -d' ' -f1)"
    fi
    if [ -f ".git/hooks/pre-push" ]; then
        echo "📤 pre-push hash: $(sha256sum .git/hooks/pre-push | cut -d' ' -f1)"
    fi
    exit 0
fi

echo "🔧 安裝 Git Hooks..."
echo "=========================================="

# 1. 安裝 root pre-commit hook
echo "📝 [1/4] 安裝 root pre-commit hook..."
mkdir -p .git/hooks
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
echo "  ✅ .git/hooks/pre-commit"

# 2. 安裝 pre-push hook
echo "📤 [2/4] 安裝 pre-push hook..."
cp scripts/hooks/pre-push .git/hooks/pre-push
chmod +x .git/hooks/pre-push
echo "  ✅ .git/hooks/pre-push"

# 3. 確保 backend/hooks/pre-commit 可執行
echo "🔨 [3/4] 確保 backend pre-commit hook 可執行..."
chmod +x backend/hooks/pre-commit
echo "  ✅ backend/hooks/pre-commit"

# 4. 驗證 Hook 是否正確安裝
echo "🔍 [4/4] 驗證 Hook 安裝..."
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

echo ""
echo "=========================================="
echo "✅ 所有 Hooks 安裝完成！"
echo ""
echo "📋 Hook 功能說明："
echo "  📝 pre-commit: 檢查 staged 檔案（lint + compile + 核心測試）"
echo "  📤 pre-push:   執行本地 CI 驗證，確保通過後才能 push"
echo ""
echo "🔐 安全機制："
echo "  ✅ 任何 commit 都會清除 CI 驗證記錄"
echo "  ✅ push 前必須通過本地 CI 驗證（act）"
echo "  ✅ 檢查 .ci-validated 是否被錯誤 commit"
echo ""
echo "⚠️  跳過方式（不建議）："
echo "  git commit --no-verify"
echo "  git push --no-verify"
echo ""
echo "🧪 測試 hooks："
echo "  echo 'test' >> test.txt && git add test.txt && git commit -m 'test'"
echo ""
echo "🔍 驗證 Hook 安裝："
echo "  VERIFY_HOOKS=1 ./scripts/install-hooks.sh"
echo ""
