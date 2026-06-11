# Local LLM 本地部署指南
# AISDLC v0.09 - 開發專注版
# 用途：在本機運行輕量級 LLM，供開發測試 AI 功能使用
# 目標：避免依賴外部 API、降低開發成本、提升測試穩定性

---

## 📋 概述

本方案使用 **llama.cpp** (Docker image) 搭配 **Qwen2.5 GGUF 量化模型**，在本機運行輕量級 LLM。
所有測試皆在本地完成，無需呼叫 OpenAI/Anthropic 等外部 API。

---

## 🎯 適用情境

| 情境 | 效益 |
|------|------|
| 開發 AI 聊天機器人 | 不需付費 API 額度 |
| 整合測試 LLM 回應 | 固定回傳值，測試穩定 |
| CI/CD 整合 | 本機即可驗證 LLM 相關代碼 |
| 隱私敏感資料 | 資料不外傳 |
| 學習/研究 | 無 API 配額限制 |

---

## 💻 硬體需求

### 最低需求（1.5B 模型，Q4 量化）
- **RAM**: 8GB（含 OS + IDE + Docker）
- **CPU**: 4 cores
- **磁碟**: 2GB（模型檔）
- **適用**: M1/M2 MacBook Air、Linux VPS

### 建議需求（7B 模型，Q4 量化）
- **RAM**: 16GB
- **CPU**: 8 cores
- **GPU** (選配): Apple Silicon Metal / NVIDIA CUDA
- **磁碟**: 5GB

---

## 🚀 快速啟動

### 1. 下載 GGUF 量化模型

```bash
# 建立模型目錄
mkdir -p ~/models

# 下載 Qwen2.5-1.5B-Instruct (Q4_K_M 量化，約 1GB)
curl -L -o ~/models/qwen2.5-1.5b-instruct-q4_k_m.gguf \
  https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf

# 或下載 7B 版本（需更多 RAM）
curl -L -o ~/models/qwen2.5-7b-instruct-q4_k_m.gguf \
  https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf
```

### 2. 啟動 Local LLM 容器

```bash
# 啟動 Mock 服務 + Local LLM
docker compose -f docker-compose.yml -f docker-compose.mock.yml --profile with-llm up -d local-llm

# 或單獨啟動
docker compose -f docker-compose.mock.yml --profile with-llm up -d local-llm
```

### 3. 驗證 LLM 服務

```bash
# 健康檢查
curl http://localhost:8081/health

# 發送測試請求（OpenAI 相容 API）
curl -X POST http://localhost:8081/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "你好，請介紹一下你自己"}
    ],
    "temperature": 0.7,
    "max_tokens": 200
  }'
```

---

## 🔌 在 Spring Boot 中整合

### application-dev.yml 設定

```yaml
# 啟用 Local LLM
ai:
  provider: local-llm
  base-url: http://localhost:8081/v1
  model: qwen2.5-1.5b-instruct
  api-key: not-required  # 本機無需 API key
  timeout: 30000
  max-tokens: 2000
```

### 使用方式

```java
@Service
public class ChatService {

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.model}")
    private String model;

    private final RestTemplate restTemplate;

    public ChatService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public String chat(String userMessage) {
        // 呼叫 OpenAI 相容 API
        Map<String, Object> request = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", 0.7
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/chat/completions",
            request,
            Map.class
        );

        // 解析回應
        Map<String, Object> choice = ((List<Map<String, Object>>) response.getBody().get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        return (String) message.get("content");
    }
}
```

---

## 🧪 測試 Local LLM

### 單元測試

```java
@SpringBootTest
@ActiveProfiles("test")
class ChatServiceTest {

    @Autowired
    private ChatService chatService;

    @Test
    void testChatWithLocalLLM() {
        String response = chatService.chat("1 + 1 = ?");
        assertThat(response).contains("2");
    }
}
```

### 整合測試（使用 Testcontainers）

```java
@Testcontainers
class LocalLLMIntegrationTest {

    @Container
    static GenericContainer<?> localLLM = new GenericContainer<>("ghcr.io/ggerganov/llama.cpp:server")
        .withExposedPorts(8080)
        .withClasspathResourceMapping("models/", "/models/", BindMode.READ_ONLY)
        .withCommand("--server", "--host", "0.0.0.0", "--port", "8080", "-m", "/models/qwen2.5-1.5b-instruct-q4_k_m.gguf")
        .waitingFor(Wait.forHttp("/health"));

    @Test
    void shouldGenerateResponse() {
        String url = "http://localhost:" + localLLM.getMappedPort(8080) + "/v1/chat/completions";
        // ... 呼叫 API 並驗證
    }
}
```

---

## 🔧 進階配置

### 啟用 GPU 加速（M1/M2 Mac）

修改 `docker-compose.mock.yml`：

```yaml
local-llm:
  image: ghcr.io/ggerganov/llama.cpp:server
  # ... 其他設定
  devices:
    - /dev/dri:/dev/dri  # Intel/AMD GPU
    # M1/M2 Mac 需使用 Metal 編譯版本（非官方，需自行 build）
  environment:
    - LLAMA_ARG_N_GPU_LAYERS=99  # 全部層使用 GPU
```

### 切換不同模型

```bash
# 7B 模型（需要 16GB+ RAM）
docker compose -f docker-compose.mock.yml --profile with-llm run local-llm \
  -m /models/qwen2.5-7b-instruct-q4_k_m.gguf --ctx-size 4096

# 中文優化模型（如 Yi-1.5-9B）
docker compose -f docker-compose.mock.yml --profile with-llm run local-llm \
  -m /models/Yi-1.5-9B-Chat-q4_k_m.gguf
```

---

## 📊 效能基準（M1 MacBook Air 8GB）

| 模型 | 量化 | 速度 (tokens/s) | RAM 使用 |
|------|------|----------------|---------|
| Qwen2.5-1.5B | Q4_K_M | ~15 | ~2GB |
| Qwen2.5-7B | Q4_K_M | ~5 | ~5GB |
| Llama-3.2-3B | Q4_K_M | ~10 | ~3GB |
| Yi-1.5-9B | Q4_K_M | ~3 | ~7GB |

---

## 🐛 常見問題

### Q1: 容器啟動後立即退出
**原因**: 模型檔案不存在或路徑錯誤
**解決**:
```bash
# 檢查模型檔案
ls -lh ~/models/

# 確保 docker-compose.mock.yml 中的 volumes 路徑正確
```

### Q2: 回應速度太慢
**優化**:
- 降低 `LLAMA_ARG_CTX_SIZE`（如 1024）
- 降低 `max_tokens` 設定
- 使用更小的模型（1.5B 而非 7B）

### Q3: 中文回應品質不佳
**建議**:
- 使用 Qwen2.5 系列（中文優化）
- 嘗試 Yi 系列
- 避免使用 Llama 系列處理中文

---

## 🔗 參考資源

- **llama.cpp GitHub**: https://github.com/ggerganov/llama.cpp
- **Qwen2.5 GGUF**: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
- **量化技術文檔**: https://github.com/ggerganov/llama.cpp/blob/master/quantization.md
- **OpenAI 相容 API**: https://platform.openai.com/docs/api-reference/chat

---

## 📝 維護記錄

| 日期 | 版本 | 變更 |
|------|------|------|
| 2026-06-11 | 1.0 | 初版建立（基於 AISDLC v0.09） |
