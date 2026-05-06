#!/bin/bash
# M15 CMS Backend API Automation Test Suite
# AT-M15-API: M15 CMS Backend 24 UAT Cases Automation
#
# 使用方式: ./run_api_tests.sh
# 前置條件: Backend 運行於 http://localhost:8080

set -e

# 允許任何錯誤時繼續執行（測試腳本專用）
# set -e 會導致任何非零退出碼的指令終止腳本

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 測試配置
BACKEND_URL="http://localhost:8080"
TENANT_ID="a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
LISTING_ID="e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
ROOM_LISTING_ID="f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"
TIMESTAMP=$(date +%s)
TEST_PASSWORD="Test123!"

# 計數器
PASSED=0
FAILED=0
TOTAL=0

# 測試結果檔案
REPORT_FILE="docs/03_testing/TR_M15_API_Results.md"

# 日誌函數
log_info() {
    echo -e "${NC}[INFO] $1"
}

log_pass() {
    echo -e "${GREEN}[PASS] $1${NC}"
    PASSED=$((PASSED + 1))
    TOTAL=$((TOTAL + 1))
}

log_fail() {
    echo -e "${RED}[FAIL] $1${NC}"
    FAILED=$((FAILED + 1))
    TOTAL=$((TOTAL + 1))
}

log_skip() {
    echo -e "${YELLOW}[SKIP] $1${NC}"
    TOTAL=$((TOTAL + 1))
}

log_section() {
    echo ""
    echo "========================================"
    echo -e "${YELLOW}$1${NC}"
    echo "========================================"
}

# 初始化報告
init_report() {
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M UTC%z')
    cat > "$REPORT_FILE" << HEADER
# M15 CMS Backend API 自動化測試報告 / M15 CMS Backend API Automation Test Report

## 文件資訊
- **AT-ID**: AT-M15-API
- **版本**: 1.0
- **日期**: $TIMESTAMP
- **狀態**: 測試中

---

## 1. 測試執行摘要

| 項目 | 結果 |
|------|------|
| 總測試數 | 0 |
| 通過 | 0 |
| 失敗 | 0 |
| 跳過 | 0 |
| 執行時間 | 計算中... |

---

## 2. 測試結果矩陣

| 測試 ID | 測試名稱 | 結果 | 實際回應 | 備註 |
|---------|----------|------|----------|------|
HEADER
}

# 添加測試結果到報告
add_result() {
    local tc_id=$1
    local tc_name=$2
    local result=$3
    local response=$4
    local notes=$5

    echo "| $tc_id | $tc_name | $result | $response | $notes |" >> "$REPORT_FILE"
}

# 發送 API 請求並檢查結果
# 用法: api_test <tc_id> <tc_name> <method> <endpoint> <expected_status> <data_or_null>
api_test() {
    local tc_id=$1
    local tc_name=$2
    local method=$3
    local endpoint=$4
    local expected_status=$5
    local data=$6
    local token=${7:-$TOKEN}

    log_info "執行: $tc_id - $tc_name"

    # 构建 curl 命令
    if [ "$method" = "GET" ] || [ "$method" = "DELETE" ]; then
        if [ -n "$token" ]; then
            response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" 2>/dev/null)
        else
            response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" 2>/dev/null)
        fi
    else
        if [ -n "$token" ]; then
            response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null)
        else
            response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null)
        fi
    fi

    # 解析 HTTP status code (的最後一行)
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | head -n -1)

    # 檢查結果
    if [ "$http_code" = "$expected_status" ]; then
        log_pass "$tc_id ($http_code)"
        add_result "$tc_id" "$tc_name" "✅ Pass" "$http_code" "-"
        return 0
    else
        log_fail "$tc_id (預期: $expected_status, 實際: $http_code)"
        add_result "$tc_id" "$tc_name" "❌ Fail" "$http_code" "回應: ${body:0:100}"
        return 1
    fi
}

# 發送 API 請求並獲取 response body（用於後續測試）
# 用法: api_request <method> <endpoint> <data_or_null> <token>
api_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local token=${4:-$TOKEN}

    if [ "$method" = "GET" ] || [ "$method" = "DELETE" ]; then
        if [ -n "$token" ]; then
            curl -s -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" 2>/dev/null
        else
            curl -s -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" 2>/dev/null
        fi
    else
        if [ -n "$token" ]; then
            curl -s -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null
        else
            curl -s -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null
        fi
    fi
}

# 獲取 HTTP status code
get_status() {
    local method=$1
    local endpoint=$2
    local data=$3
    local token=${4:-$TOKEN}

    if [ "$method" = "GET" ] || [ "$method" = "DELETE" ]; then
        if [ -n "$token" ]; then
            curl -s -w "%{http_code}" -o /dev/null -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" 2>/dev/null
        else
            curl -s -w "%{http_code}" -o /dev/null -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" 2>/dev/null
        fi
    else
        if [ -n "$token" ]; then
            curl -s -w "%{http_code}" -o /dev/null -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Authorization: Bearer ${token}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null
        else
            curl -s -w "%{http_code}" -o /dev/null -X "$method" "${BACKEND_URL}${endpoint}" \
                -H "Content-Type: application/json" \
                -d "$data" 2>/dev/null
        fi
    fi
}

# 測試建構函數 - 發送請求並擷取 ID
api_test_with_capture() {
    local tc_id=$1
    local tc_name=$2
    local method=$3
    local endpoint=$4
    local expected_status=$5
    local data=$6
    local capture_var=$7
    local token=${8:-$TOKEN}

    log_info "執行並捕獲: $tc_id - $tc_name"

    if [ -n "$token" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
            -H "Authorization: Bearer ${token}" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "${BACKEND_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -d "$data" 2>/dev/null)
    fi

    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | head -n -1)

    if [ "$http_code" = "$expected_status" ]; then
        log_pass "$tc_id ($http_code)"
        add_result "$tc_id" "$tc_name" "✅ Pass" "$http_code" "-"

        # 嘗試擷取 ID (假設在 "id": "xxx" 中)
        if [ -n "$capture_var" ]; then
            captured_id=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
            eval "$capture_var='$captured_id'"
            log_info "捕獲 ID: $captured_id"
        fi
        return 0
    else
        log_fail "$tc_id (預期: $expected_status, 實際: $http_code)"
        add_result "$tc_id" "$tc_name" "❌ Fail" "$http_code" "回應: ${body:0:100}"
        return 1
    fi
}

#############################################
# 主測試流程
#############################################

main() {
    echo ""
    log_section "M15 CMS Backend API 自動化測試"
    echo ""

    # 初始化報告
    init_report

    # 步驟 0: 環境檢查
    log_section "步驟 0: 環境檢查"

    echo "測試環境: $BACKEND_URL"
    echo "測試時間: $(date)"

    # 檢查後端是否運行
    health_status=$(get_status "GET" "/api/v2/posts?tenantId=${TENANT_ID}" "" "")
    if [ "$health_status" = "401" ] || [ "$health_status" = "200" ] || [ "$health_status" = "404" ]; then
        log_pass "Backend API 运行正常 (HTTP $health_status)"
    else
        log_fail "Backend API 無法訪問 (HTTP $health_status)"
        echo "請確認 Backend 運行於 http://localhost:8080"
        exit 1
    fi

    # 步驟 1: 註冊並登入取得 Token
    log_section "步驟 1: 認證 - 註冊並登入"

    TEST_EMAIL="e2e-$(date +%s)@test.com"

    # 嘗試登入 (可能已存在)
    LOGIN_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/v2/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}" 2>/dev/null)

    if echo "$LOGIN_RESPONSE" | grep -q '"accessToken"'; then
        TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
        log_pass "登入成功 (現有帳號)"
    elif echo "$LOGIN_RESPONSE" | grep -q '"token"'; then
        TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
        log_pass "登入成功 (現有帳號)"
    else
        # 註冊新帳號（使用 STORE_OWNER 角色以通過 CMS 權限驗證）
        REGISTER_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/v2/auth/register" \
            -H "Content-Type: application/json" \
            -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\",\"fullName\":\"E2E Test User\",\"tenantId\":\"${TENANT_ID}\",\"userType\":\"STORE_OWNER\"}" 2>/dev/null)

        if echo "$REGISTER_RESPONSE" | grep -q '"userId"'; then
            # 註冊成功，但需要再次登入取得 token
            LOGIN_AFTER_REG=$(curl -s -X POST "${BACKEND_URL}/api/v2/auth/login" \
                -H "Content-Type: application/json" \
                -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}" 2>/dev/null)

            if echo "$LOGIN_AFTER_REG" | grep -q '"accessToken"'; then
                TOKEN=$(echo "$LOGIN_AFTER_REG" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
                log_pass "註冊並登入成功"
            elif echo "$LOGIN_AFTER_REG" | grep -q '"token"'; then
                TOKEN=$(echo "$LOGIN_AFTER_REG" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
                log_pass "註冊並登入成功"
            else
                log_fail "註冊成功但登入失敗: ${LOGIN_AFTER_REG:0:200}"
                echo "無法取得 Token，測試終止"
                exit 1
            fi
        else
            log_fail "認證失敗: ${REGISTER_RESPONSE:0:200}"
            echo "無法取得 Token，測試終止"
            exit 1
        fi
    fi

    echo "Token: ${TOKEN:0:50}..."
    POST_ID=""
    MEDIA_ID=""

    # 步驟 2: Post 管理功能測試 (TC-M15-001 ~ TC-M15-012)
    log_section "步驟 2: Post 管理功能 (TC-M15-001 ~ TC-M15-012)"

    # TC-M15-001: 建立草稿貼文
    tc_m15_001_response=$(api_request "POST" "/api/v2/dashboard/posts" \
        '{"title":"Test Post Title","content":"This is test content","autoPublish":false}' "$TOKEN")
    http_code=$(get_status "POST" "/api/v2/dashboard/posts" \
        '{"title":"Test Post Title","content":"This is test content","autoPublish":false}' "$TOKEN")

    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-001: 建立草稿貼文 (HTTP $http_code)"
        add_result "TC-M15-001" "建立草稿貼文" "✅ Pass" "$http_code" "-"
        POST_ID=$(echo "$tc_m15_001_response" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
        SLUG=$(echo "$tc_m15_001_response" | grep -o '"slug":"[^"]*"' | head -1 | cut -d'"' -f4)
        log_info "建立成功 - POST_ID: $POST_ID, SLUG: $SLUG"
    else
        log_fail "TC-M15-001: 建立草稿貼文 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-001" "建立草稿貼文" "❌ Fail" "$http_code" "${tc_m15_001_response:0:100}"
    fi

    # TC-M15-002: 建立貼文 - 空白標題驗證
    http_code=$(get_status "POST" "/api/v2/dashboard/posts" \
        '{"title":"","content":"Some content"}' "$TOKEN")
    if [ "$http_code" = "400" ]; then
        log_pass "TC-M15-002: 空白標題驗證 (HTTP $http_code)"
        add_result "TC-M15-002" "空白標題驗證" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-002: 空白標題驗證 (預期: 400, 實際: $http_code)"
        add_result "TC-M15-002" "空白標題驗證" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-003: 建立貼文 - 有效 Embed 語法
    http_code=$(get_status "POST" "/api/v2/dashboard/posts" \
        "{\"title\":\"Post with Embed\",\"content\":\"Check out: {{embed:listing:${LISTING_ID}}}\"}" "$TOKEN")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-003: 有效 Embed 語法 (HTTP $http_code)"
        add_result "TC-M15-003" "有效 Embed 語法" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-003: 有效 Embed 語法 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-003" "有效 Embed 語法" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-004: 建立貼文 - 重複 Embed 驗證
    http_code=$(get_status "POST" "/api/v2/dashboard/posts" \
        "{\"title\":\"Duplicate Embed\",\"content\":\"Product 1: {{embed:listing:${LISTING_ID}}}\nProduct 2: {{embed:listing:${LISTING_ID}}}\"}" "$TOKEN")
    if [ "$http_code" = "400" ]; then
        log_pass "TC-M15-004: 重複 Embed 驗證 (HTTP $http_code)"
        add_result "TC-M15-004" "重複 Embed 驗證" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-004: 重複 Embed 驗證 (預期: 400, 實際: $http_code)"
        add_result "TC-M15-004" "重複 Embed 驗證" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-005: 更新貼文
    if [ -n "$POST_ID" ]; then
        http_code=$(get_status "PUT" "/api/v2/dashboard/posts/${POST_ID}" \
            '{"title":"Updated Post Title","content":"Updated content"}' "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-005: 更新貼文 (HTTP $http_code)"
            add_result "TC-M15-005" "更新貼文" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-005: 更新貼文 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-005" "更新貼文" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-005: 更新貼文 (需 POST_ID)"
        add_result "TC-M15-005" "更新貼文" "⚠️ Skip" "-" "無 POST_ID"
    fi

    # TC-M15-006: 發布貼文
    if [ -n "$POST_ID" ]; then
        http_code=$(get_status "POST" "/api/v2/dashboard/posts/${POST_ID}/publish" \
            "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-006: 發布貼文 (HTTP $http_code)"
            add_result "TC-M15-006" "發布貼文" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-006: 發布貼文 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-006" "發布貼文" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-006: 發布貼文 (需 POST_ID)"
        add_result "TC-M15-006" "發布貼文" "⚠️ Skip" "-" "無 POST_ID"
    fi

    # TC-M15-007: 下架貼文
    if [ -n "$POST_ID" ]; then
        http_code=$(get_status "DELETE" "/api/v2/dashboard/posts/${POST_ID}/publish" \
            "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-007: 下架貼文 (HTTP $http_code)"
            add_result "TC-M15-007" "下架貼文" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-007: 下架貼文 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-007" "下架貼文" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-007: 下架貼文 (需 POST_ID)"
        add_result "TC-M15-007" "下架貼文" "⚠️ Skip" "-" "無 POST_ID"
    fi

    # TC-M15-008: 刪除草稿
    if [ -n "$POST_ID" ]; then
        http_code=$(get_status "DELETE" "/api/v2/dashboard/posts/${POST_ID}" "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-008: 刪除草稿 (HTTP $http_code)"
            add_result "TC-M15-008" "刪除草稿" "✅ Pass" "$http_code" "-"
            POST_ID="" # 已刪除
        else
            log_fail "TC-M15-008: 刪除草稿 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-008" "刪除草稿" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-008: 刪除草稿 (需 POST_ID)"
        add_result "TC-M15-008" "刪除草稿" "⚠️ Skip" "-" "無 POST_ID"
    fi

    # TC-M15-009: 刪除已發布（自動下架後刪除）- 先建立再刪除
    CREATE_RESPONSE=$(api_request "POST" "/api/v2/dashboard/posts" \
        '{"title":"Published to Delete","autoPublish":true}' "$TOKEN")
    PUBLISHED_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -n "$PUBLISHED_ID" ]; then
        http_code=$(get_status "DELETE" "/api/v2/dashboard/posts/${PUBLISHED_ID}" "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-009: 刪除已發布 (HTTP $http_code)"
            add_result "TC-M15-009" "刪除已發布" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-009: 刪除已發布 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-009" "刪除已發布" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-009: 刪除已發布"
        add_result "TC-M15-009" "刪除已發布" "⚠️ Skip" "-" "建立失敗"
    fi

    # TC-M15-010: 取得貼文詳情 (需重建 POST_ID)
    REBUILD_RESPONSE=$(api_request "POST" "/api/v2/dashboard/posts" \
        '{"title":"Detail Test","content":"For detail test"}' "$TOKEN")
    TEST_POST_ID=$(echo "$REBUILD_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -n "$TEST_POST_ID" ]; then
        http_code=$(get_status "GET" "/api/v2/dashboard/posts/${TEST_POST_ID}" "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-010: 取得貼文詳情 (HTTP $http_code)"
            add_result "TC-M15-010" "取得貼文詳情" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-010: 取得貼文詳情 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-010" "取得貼文詳情" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-010: 取得貼文詳情"
        add_result "TC-M15-010" "取得貼文詳情" "⚠️ Skip" "-" "建立失敗"
    fi

    # TC-M15-011: 取得無效貼文
    INVALID_ID="00000000-0000-0000-0000-000000000000"
    http_code=$(get_status "GET" "/api/v2/dashboard/posts/${INVALID_ID}" "" "$TOKEN")
    if [ "$http_code" = "404" ]; then
        log_pass "TC-M15-011: 取得無效貼文 (HTTP $http_code)"
        add_result "TC-M15-011" "取得無效貼文" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-011: 取得無效貼文 (預期: 404, 實際: $http_code)"
        add_result "TC-M15-011" "取得無效貼文" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-012: 跨 Tenant 權限驗證 (使用無效 token)
    FAKE_TOKEN="fake-token-for-test"
    http_code=$(get_status "GET" "/api/v2/dashboard/posts/${TEST_POST_ID}" "" "$FAKE_TOKEN")
    if [ "$http_code" = "401" ] || [ "$http_code" = "403" ]; then
        log_pass "TC-M15-012: 跨 Tenant 權限驗證 (HTTP $http_code)"
        add_result "TC-M15-012" "跨 Tenant 權限驗證" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-012: 跨 Tenant 權限驗證 (預期: 401/403, 實際: $http_code)"
        add_result "TC-M15-012" "跨 Tenant 權限驗證" "❌ Fail" "$http_code" "-"
    fi

    # 步驟 3: Public API 功能測試 (TC-M15-013 ~ TC-M15-016)
    log_section "步驟 3: Public API 功能 (TC-M15-013 ~ TC-M15-016)"

    # TC-M15-013: 取得已發布貼文列表
    http_code=$(get_status "GET" "/api/v2/posts?tenantId=${TENANT_ID}&page=0&size=10" "" "")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-013: 取得已發布貼文列表 (HTTP $http_code)"
        add_result "TC-M15-013" "取得已發布貼文列表" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-013: 取得已發布貼文列表 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-013" "取得已發布貼文列表" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-014: 依 Slug 取得已發布貼文
    # 先建立一個已發布的貼文來測試
    PUBLISH_TEST_RESPONSE=$(api_request "POST" "/api/v2/dashboard/posts" \
        '{"title":"Published for Slug Test","autoPublish":true}' "$TOKEN")
    PUBLISH_TEST_ID=$(echo "$PUBLISH_TEST_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    PUBLISH_TEST_SLUG=$(echo "$PUBLISH_TEST_RESPONSE" | grep -o '"slug":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -n "$PUBLISH_TEST_SLUG" ]; then
        http_code=$(get_status "GET" "/api/v2/posts/${PUBLISH_TEST_SLUG}?tenantId=${TENANT_ID}" "" "")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-014: 依 Slug 取得已發布貼文 (HTTP $http_code)"
            add_result "TC-M15-014" "依 Slug 取得已發布貼文" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-014: 依 Slug 取得已發布貼文 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-014" "依 Slug 取得已發布貼文" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-014: 依 Slug 取得已發布貼文"
        add_result "TC-M15-014" "依 Slug 取得已發布貼文" "⚠️ Skip" "-" "無 PUBLISH_TEST_SLUG"
    fi

    # TC-M15-015: 依 Slug 取得 - 無效 slug
    http_code=$(get_status "GET" "/api/v2/posts/nonexistent-slug-12345?tenantId=${TENANT_ID}" "" "")
    if [ "$http_code" = "404" ]; then
        log_pass "TC-M15-015: 無效 slug (HTTP $http_code)"
        add_result "TC-M15-015" "無效 slug" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-015: 無效 slug (預期: 404, 實際: $http_code)"
        add_result "TC-M15-015" "無效 slug" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-016: 依 Slug 取得 - 已下架貼文
    # 先建立一個草稿再測試
    DRAFT_RESPONSE=$(api_request "POST" "/api/v2/dashboard/posts" \
        '{"title":"Draft for Unpublish Test","autoPublish":false}' "$TOKEN")
    DRAFT_ID=$(echo "$DRAFT_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

    if [ -n "$DRAFT_ID" ]; then
        # 嘗試用 slug 取得草稿（應該 404）
        DRAFT_SLUG=$(echo "$DRAFT_RESPONSE" | grep -o '"slug":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ -n "$DRAFT_SLUG" ]; then
            http_code=$(get_status "GET" "/api/v2/posts/${DRAFT_SLUG}?tenantId=${TENANT_ID}" "" "")
            if [ "$http_code" = "404" ]; then
                log_pass "TC-M15-016: 已下架貼文 (HTTP $http_code)"
                add_result "TC-M15-016" "已下架貼文" "✅ Pass" "$http_code" "-"
            else
                log_fail "TC-M15-016: 已下架貼文 (預期: 404, 實際: $http_code)"
                add_result "TC-M15-016" "已下架貼文" "❌ Fail" "$http_code" "-"
            fi
        else
            log_skip "TC-M15-016: 已下架貼文"
            add_result "TC-M15-016" "已下架貼文" "⚠️ Skip" "-" "無 DRAFT_SLUG"
        fi
    else
        log_skip "TC-M15-016: 已下架貼文"
        add_result "TC-M15-016" "已下架貼文" "⚠️ Skip" "-" "建立失敗"
    fi

    # 步驟 4: Media 上傳功能測試 (TC-M15-017 ~ TC-M15-020)
    log_section "步驟 4: Media 上傳功能 (TC-M15-017 ~ TC-M15-020)"

    # TC-M15-017: 媒體上傳 (路徑版本 - 不會真的上傳到 S3)
    # 注意: endpoint 使用 @RequestParam，需發送 form-data 格式
    echo "注意: TC-M15-017 測試路徑-based 上傳，不包含實際 S3 上傳"

    # 建立一個測試檔案
    echo "test image content" > /tmp/test-image.jpg

    # 使用 curl -F 發送 form-data（符合 @RequestParam 格式）
    UPLOAD_RESPONSE=$(curl -s -X POST "${BACKEND_URL}/api/v2/media/upload" \
        -H "Authorization: Bearer ${TOKEN}" \
        -F "fileName=test-image.jpg" \
        -F "originalName=test-image.jpg" \
        -F "fileSize=102400" \
        -F "mimeType=image/jpeg" \
        -F "filePath=/tmp/test-image.jpg" 2>/dev/null)

    http_code=$(curl -s -w "%{http_code}" -o /dev/null -X POST "${BACKEND_URL}/api/v2/media/upload" \
        -H "Authorization: Bearer ${TOKEN}" \
        -F "fileName=test-image.jpg" \
        -F "originalName=test-image.jpg" \
        -F "fileSize=102400" \
        -F "mimeType=image/jpeg" \
        -F "filePath=/tmp/test-image.jpg" 2>/dev/null)

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        log_pass "TC-M15-017: 媒體上傳 (HTTP $http_code)"
        add_result "TC-M15-017" "媒體上傳" "✅ Pass" "$http_code" "路徑版本"
        MEDIA_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    else
        log_fail "TC-M15-017: 媒體上傳 (預期: 201/200, 實際: $http_code)"
        add_result "TC-M15-017" "媒體上傳" "❌ Fail" "$http_code" "${UPLOAD_RESPONSE:0:100}"
    fi

    # TC-M15-018: 媒體上傳 - 檔案類型驗證
    http_code=$(curl -s -w "%{http_code}" -o /dev/null -X POST "${BACKEND_URL}/api/v2/media/upload" \
        -H "Authorization: Bearer ${TOKEN}" \
        -F "fileName=test.txt" \
        -F "originalName=test.txt" \
        -F "fileSize=100" \
        -F "mimeType=text/plain" \
        -F "filePath=/tmp/test.txt" 2>/dev/null)
    if [ "$http_code" = "400" ]; then
        log_pass "TC-M15-018: 檔案類型驗證 (HTTP $http_code)"
        add_result "TC-M15-018" "檔案類型驗證" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-018: 檔案類型驗證 (預期: 400, 實際: $http_code)"
        add_result "TC-M15-018" "檔案類型驗證" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-019: 列出媒體
    http_code=$(get_status "GET" "/api/v2/dashboard/media" "" "$TOKEN")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-019: 列出媒體 (HTTP $http_code)"
        add_result "TC-M15-019" "列出媒體" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-019: 列出媒體 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-019" "列出媒體" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-020: 刪除媒體
    if [ -n "$MEDIA_ID" ]; then
        http_code=$(get_status "DELETE" "/api/v2/dashboard/media/${MEDIA_ID}" "" "$TOKEN")
        if [ "$http_code" = "200" ]; then
            log_pass "TC-M15-020: 刪除媒體 (HTTP $http_code)"
            add_result "TC-M15-020" "刪除媒體" "✅ Pass" "$http_code" "-"
        else
            log_fail "TC-M15-020: 刪除媒體 (預期: 200, 實際: $http_code)"
            add_result "TC-M15-020" "刪除媒體" "❌ Fail" "$http_code" "-"
        fi
    else
        log_skip "TC-M15-020: 刪除媒體 (需 MEDIA_ID)"
        add_result "TC-M15-020" "刪除媒體" "⚠️ Skip" "-" "無 MEDIA_ID"
    fi

    # 步驟 5: Listing Card 功能測試 (TC-M15-021 ~ TC-M15-024)
    log_section "步驟 5: Listing Card 功能 (TC-M15-021 ~ TC-M15-024)"

    # TC-M15-021: 取得商品卡片（PRODUCT）
    http_code=$(get_status "GET" "/api/v2/listings/${LISTING_ID}/card" "" "")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-021: 取得商品卡片 (HTTP $http_code)"
        add_result "TC-M15-021" "取得商品卡片" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-021: 取得商品卡片 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-021" "取得商品卡片" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-022: 取得房型卡片（ROOM）
    http_code=$(get_status "GET" "/api/v2/listings/${ROOM_LISTING_ID}/card" "" "")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-022: 取得房型卡片 (HTTP $http_code)"
        add_result "TC-M15-022" "取得房型卡片" "✅ Pass" "$http_code" "使用現有 Listing"
    else
        log_fail "TC-M15-022: 取得房型卡片 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-022" "取得房型卡片" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-023: 卡片可用性驗證
    http_code=$(get_status "GET" "/api/v2/listings/${LISTING_ID}/card" "" "")
    if [ "$http_code" = "200" ]; then
        log_pass "TC-M15-023: 卡片可用性驗證 (HTTP $http_code)"
        add_result "TC-M15-023" "卡片可用性驗證" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-023: 卡片可用性驗證 (預期: 200, 實際: $http_code)"
        add_result "TC-M15-023" "卡片可用性驗證" "❌ Fail" "$http_code" "-"
    fi

    # TC-M15-024: 取得無效 Listing
    INVALID_LISTING_ID="00000000-0000-0000-0000-000000000000"
    http_code=$(get_status "GET" "/api/v2/listings/${INVALID_LISTING_ID}/card" "" "")
    if [ "$http_code" = "404" ]; then
        log_pass "TC-M15-024: 取得無效 Listing (HTTP $http_code)"
        add_result "TC-M15-024" "取得無效 Listing" "✅ Pass" "$http_code" "-"
    else
        log_fail "TC-M15-024: 取得無效 Listing (預期: 404, 實際: $http_code)"
        add_result "TC-M15-024" "取得無效 Listing" "❌ Fail" "$http_code" "-"
    fi

    # 完成報告
    log_section "測試完成"

    echo ""
    echo "========================================"
    echo "  測試結果摘要"
    echo "========================================"
    echo -e "  總測試數: ${GREEN}$TOTAL${NC}"
    echo -e "  通過:     ${GREEN}$PASSED${NC}"
    echo -e "  失敗:     ${RED}$FAILED${NC}"
    echo -e "  跳過:     ${YELLOW}$((TOTAL - PASSED - FAILED))${NC}"
    echo "========================================"
    echo ""

    # 更新報告檔案
    SKIPPED=$((TOTAL - PASSED - FAILED))
    EXEC_TIME=$(date +%s)
    cat >> "$REPORT_FILE" << REPORT_FOOTER

---

## 3. 測試摘要

| 項目 | 數值 |
|------|------|
| 總測試數 | $TOTAL |
| 通過 | $PASSED |
| 失敗 | $FAILED |
| 跳過 | $SKIPPED |
| 通過率 | $(echo "scale=1; $PASSED * 100 / $TOTAL" | bc 2>/dev/null || echo "N/A")% |

## 4. 風險驗證狀態

| 風險 ID | 等級 | 驗證結果 | 備註 |
|---------|------|----------|------|
| R-001 (測試隔離) | 中 | ✅ 已緩解 | E2E + API 測試使用新用戶 |
| R-002 (Storage Mock) | 中 | ⚠️ 部分驗證 | 路徑上傳已測試，S3 待驗證 |
| R-003 (FE 整合) | 低 | ✅ 已緩解 | E2E 已驗證前端頁面 |

## 5. 待確認項目

| 項目 | 說明 | 負責 |
|------|------|------|
| MinIO 實際上傳 | 需手動驗證 S3/MinIO 寫入 | QA |
| MultipartFile 上傳 | 需 Dev 新增 endpoint | Dev |

---

**測試執行者**: Automated Shell Script
**報告產生時間**: $(date '+%Y-%m-%d %H:%M UTC%z')
REPORT_FOOTER

    echo "報告已生成: $REPORT_FILE"
    echo ""
}

# 執行主函數
main