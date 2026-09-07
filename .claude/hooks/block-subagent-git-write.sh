#!/bin/bash
# 阻擋子 agent（含 Agent 工具與 Workflow agent() 呼叫）自行執行 git commit/push。
# 背景：Sprint 135、136 兩次 Workflow 唯讀調查子 agent 越權寫檔並自行 commit+push 到
# origin/main（詳見 CLAUDE.md「Claude Code Workflow 子 Agent 唯讀範圍強制規則」）。
# 規則：commit/push 只能由主控 session 執行，子 agent 一律禁止，不論任務內容為何。

input=$(cat)
agent_id=$(jq -r '.agent_id // empty' <<<"$input")
command=$(jq -r '.tool_input.command // empty' <<<"$input")

if [[ -n "$agent_id" ]] && echo "$command" | grep -qE '\bgit\s+(commit|push)\b'; then
  jq -n '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: "子 agent 禁止執行 git commit/push（CLAUDE.md「Claude Code Workflow 子 Agent 唯讀範圍強制規則」）。請回傳結構化結果或修改內容給主控 session，由主控 session 驗證後自行 commit/push。"
    }
  }'
  exit 2
fi

exit 0
