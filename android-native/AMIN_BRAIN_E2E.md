# AMIN Brain subscription CLI 安全端到端驗證

## 驗證範圍

本文件記錄 AMIN Brain Step 10 與 Step 11 的安全端到端驗證方式。這是訂閱制 CLI 協作流程的驗證，不是正式發布，也不代表 Step 10 或 Step 11 的產品功能已完成。

- Step 10 驗證 Brain Runtime 的任務核准、Agent 選擇、dispatch、Draft PR 與 callback 閉環。
- Step 11 驗證 Capability Runtime 如何記錄 Codex 與 Claude Code 的分工、驗證責任與可審查證據；本次不進行能力評分、訓練、認證或自主授權。

全程使用本機 Codex 與 Claude Code 的訂閱制 CLI 登入。不讀取、傳遞或使用 `OPENAI_API_KEY` 與 `ANTHROPIC_API_KEY`，不把登入狀態、token 或其他憑證寫入候選變更、PR 或 callback。

## 同一 TASK identity

從人工核准到兩個 CLI 分工、Draft PR 審查與 callback 回報，必須沿用同一個 `task_id`。狀態變化只更新該 `TASK`，不得建立替代 TASK。

```text
TASK(suggested)
→ same TASK(approved)
→ same TASK(queued)
→ same TASK(running)
→ same TASK(reviewing|waiting_owner)
→ same TASK(completed|failed|cancelled)
```

每次 CLI 執行、重試或重新指派都可以建立新的 `run_id`，但必須保留原 `task_id`。每個 callback 至少應帶回 `task_id`、`run_id`、`agent_id`、執行結果、驗證摘要及可審查證據位置，以確保 Work Graph 可將回報對應到原任務。

## Codex 與 Claude Code 分工

1. Codex 使用本機訂閱 CLI 在隔離工作樹中實作已核准的最小變更，執行與變更風險相稱的檢查，並回報變更檔案、通過、失敗或未執行的檢查。
2. Claude Code 使用其本機訂閱 CLI 進行獨立審查，核對核准規格、修改範圍、TASK identity、安全邊界與驗證證據，並以 callback 回報通過、需修正或需要 owner 決策。
3. 任一 Agent 的重試或重新指派都要保留前次 Run 的審計軌跡，不得覆蓋不利結果，也不得藉由更換 Agent 改變 TASK identity。

此分工是 Step 11 能力資料的可審查輸入，不會單憑一次成功就授予 Agent 自主發布、簽章或合併權限。

## Draft PR 與 callback 閉環

安全驗證的邏輯閉環為：

```text
owner approval
→ dispatch with task_id and a new run_id
→ Codex implementation callback
→ Draft PR as review-only evidence
→ Claude Code independent-review callback
→ Brain updates the same TASK
→ completed, failed, or waiting_owner
```

Draft PR 只是審查與 CI-only evidence 載體。建立 Draft PR、編譯成功、測試通過或產生 artifact 均不會自動完成 TASK，也不會將 Draft PR 轉為可合併或可發布狀態。callback 僅回報結果並驅動已登錄的 Step 10 狀態轉換；它不得直接合併 PR、推送 `main`、修改 release manifest、簽章、發布或繞過 owner approval。

若驗證失敗、缺少必要證據、遇到規格歧義或需要擴大權限，callback 必須將同一 TASK 標示為 `failed` 或 `waiting_owner`，不得宣告完成。

## 本次安全邊界

- 只保留未提交的隔離工作樹變更，供決定性驗證與獨立審查。
- 不 commit、push、建立或合併 PR；不簽章、發布或存取憑證。
- 不修改 APK 或 Runtime 正式版本，不修改 `native-release-manifest.json` 或 `runtime-manifest.json`。
- 不將本文件視為 Step 10、Step 11、APK 或 Runtime 的完成或發布證明。

