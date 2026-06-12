#!/usr/bin/env bash
# =============================================
# AISDLC v0.09: Local LLM 模型下載腳本
# 用途：冪等地下載 Qwen2.5 GGUF 量化模型
# 使用方式：./scripts/download-llm-model.sh
# 環境變數：
#   LLM_MODEL_DIR   模型存放目錄（預設 ~/models）
#   LLM_MODEL_SIZE  模型大小（1.5b | 7b，預設 1.5b）
#   FORCE_REDOWNLOAD=1  強制重新下載
# =============================================

set -e

MODEL_DIR="${LLM_MODEL_DIR:-$HOME/models}"
MODEL_SIZE="${LLM_MODEL_SIZE:-1.5b}"

case "$MODEL_SIZE" in
  1.5b|1.5B)
    MODEL_FILE="qwen2.5-1.5b-instruct-q4_k_m.gguf"
    MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    MODEL_SIZE_HUMAN="~1GB"
    RAM_REQ="8GB+"
    ;;
  7b|7B)
    MODEL_FILE="qwen2.5-7b-instruct-q4_k_m.gguf"
    MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf"
    MODEL_SIZE_HUMAN="~4.5GB"
    RAM_REQ="16GB+"
    ;;
  *)
    echo "❌ 不支援的模型大小: $MODEL_SIZE (請使用 1.5b 或 7b)"
    exit 1
    ;;
esac

MODEL_PATH="$MODEL_DIR/$MODEL_FILE"

echo "🤖 Local LLM 模型下載腳本"
echo "=========================================="
echo "  模型: Qwen2.5-${MODEL_SIZE}-Instruct (Q4_K_M 量化)"
echo "  大小: $MODEL_SIZE_HUMAN"
echo "  需求: $RAM_REQ RAM"
echo "  目標: $MODEL_PATH"
echo ""

# 1. 冪等性檢查
if [ -f "$MODEL_PATH" ] && [ "$FORCE_REDOWNLOAD" != "1" ]; then
  EXISTING_SIZE=$(du -h "$MODEL_PATH" | cut -f1)
  echo "✅ 模型已存在: $MODEL_PATH ($EXISTING_SIZE)"
  echo "   強制重新下載：FORCE_REDOWNLOAD=1 $0"
  exit 0
fi

# 2. 建立目錄
mkdir -p "$MODEL_DIR"

# 3. 檢查磁碟空間
echo "🔍 檢查磁碟空間..."
REQUIRED_MB=1500
if [ "$MODEL_SIZE" = "7b" ] || [ "$MODEL_SIZE" = "7B" ]; then
  REQUIRED_MB=5500
fi

AVAILABLE_MB=$(df -m "$MODEL_DIR" | tail -1 | awk '{print $4}')
if [ "$AVAILABLE_MB" -lt "$REQUIRED_MB" ]; then
  echo "❌ 磁碟空間不足：需要 ${REQUIRED_MB}MB，可用 ${AVAILABLE_MB}MB"
  exit 1
fi
echo "   可用空間: ${AVAILABLE_MB}MB (需 ${REQUIRED_MB}MB)"

# 4. 檢查工具
echo "🔍 檢查下載工具..."
if command -v curl >/dev/null 2>&1; then
  DOWNLOAD_CMD="curl -L -C - -o"
elif command -v wget >/dev/null 2>&1; then
  DOWNLOAD_CMD="wget -c -O"
else
  echo "❌ 找不到 curl 或 wget"
  exit 1
fi
echo "   使用: ${DOWNLOAD_CMD%% *}"

# 5. 開始下載
echo ""
echo "📥 開始下載（可隨時 Ctrl+C 中斷，下次再執行會續傳）..."
echo "   URL: $MODEL_URL"
echo ""

if command -v curl >/dev/null 2>&1; then
  curl -L -C - --progress-bar -o "$MODEL_PATH" "$MODEL_URL"
else
  wget -c -O "$MODEL_PATH" "$MODEL_URL"
fi

# 6. 驗證下載
echo ""
echo "🔍 驗證下載..."
if [ ! -f "$MODEL_PATH" ]; then
  echo "❌ 下載失敗：找不到檔案 $MODEL_PATH"
  exit 1
fi

FILE_SIZE=$(du -h "$MODEL_PATH" | cut -f1)
FILE_SIZE_MB=$(du -m "$MODEL_PATH" | cut -f1)

# GGUF 魔數檢查（"GGUF" at offset 0）
MAGIC=$(head -c 4 "$MODEL_PATH" 2>/dev/null | xxd -p)
if [ "$MAGIC" = "47475546" ]; then
  echo "✅ GGUF 格式驗證通過（magic: GGUF）"
else
  echo "⚠️  GGUF 魔數不符（magic: $MAGIC），檔案可能損壞"
  echo "   建議刪除後重新下載：rm $MODEL_PATH && $0"
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ 下載完成！"
echo "   路徑: $MODEL_PATH"
echo "   大小: $FILE_SIZE"
echo ""
echo "🚀 啟動 Local LLM："
echo "   make up-mock-with-llm"
echo "   或：docker compose -f docker-compose.yml -f docker-compose.mock.yml --profile with-llm up -d"
echo ""
echo "🧪 測試 LLM："
echo "   curl -X POST http://localhost:8081/v1/chat/completions \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}'"
echo "=========================================="
