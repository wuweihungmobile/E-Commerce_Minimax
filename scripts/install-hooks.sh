#!/usr/bin/env sh
# =============================================
# AISDLC v0.09: Hook 安裝腳本
# 安裝所有 Git Hooks（pre-commit + pre-push）
# 使用方式：./scripts/install-hooks.sh
# =============================================

set -e

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

echo "🔧 安裝 Git Hooks..."
echo "=========================================="

# 1. 安裝 root pre-commit hook
echo "📝 [1/3] 安裝 root pre-commit hook..."
mkdir -p .git/hooks
cp scripts/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
echo "  ✅ .git/hooks/pre-commit"

# 2. 安裝 pre-push hook
echo "📤 [2/3] 安裝 pre-push hook..."
cp scripts/hooks/pre-push .git/hooks/pre-push
chmod +x .git/hooks/pre-push
echo "  ✅ .git/hooks/pre-push"

# 3. 確保 backend/hooks/pre-commit 可執行
echo "🔨 [3/3] 確保 backend pre-commit hook 可執行..."
chmod +x backend/hooks/pre-commit
echo "  ✅ backend/hooks/pre-commit"

# 4. 確保 frontend husky 已初始化
if [ -f "frontend/package.json" ] && grep -q '"husky"' frontend/package.json 2>/dev/null; then
  echo ""
  echo "📦 Frontend 使用 husky，執行 npx husky install..."
  (cd frontend && npx husky install 2>/dev/null || true)
fi

echo ""
echo "=========================================="
echo "✅ 所有 Hooks 安裝完成！"
echo ""
echo "📋 說明："
echo "  - pre-commit: 檢查 staged 檔案（lint + compile + 核心測試）"
echo "  - pre-push:   執行 act CI 模擬或快速檢查"
echo "  - 跳過方式：git commit --no-verify / git push --no-verify"
echo ""
echo "🧪 測試 hooks："
echo "  echo 'test' >> test.txt && git add test.txt && git commit -m 'test' --allow-empty"
echo ""
