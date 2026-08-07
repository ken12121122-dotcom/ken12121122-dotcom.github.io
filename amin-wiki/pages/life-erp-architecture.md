# Life ERP 資料契約 × n8n × 知識庫架構 v0.3

> 狀態：`generated / candidate`，尚未人工核准。  
> 這是跨領域 Life ERP 節點。因目前手機 Wiki 關聯圖固定只讀 `amin-wiki/pages`，依使用者 2026-08-07 明確要求暫時放在此熱更新目錄；不代表它屬於 Amin Pocket GBA 技術域。

## 一句話

Life ERP 不是「所有東西都塞進 n8n」；目前架構是：**資料契約先定義共同語言，WF-001 負責治理，n8n 負責流程編排，Knowledge Base 負責累積與取回上下文。**

## 主架構

```text
使用者
  │ 最終人工核准
  ▼
ChatGPT
  │ Architecture / Governance
  ▼
Google Sheets 治理工作簿
  │ Source of Truth
  │ 02 Universal Contract
  │ 03 Node Type Registry
  │ 04 Relation Type Registry
  │ 05 Domain Registry
  │ 06 Status Registry
  ▼
WF-001 Contract Approval Pipeline
  │ 驗證 / Change Set / Approval Gate
  ├─ 未核准 → STOP
  └─ approved
       ▼
Approved Contract
       │
       ├─→ MD / YAML / JSON / JSON Schema
       └─→ IMPLEMENTATION_HANDOFF
                 ▼
              Claude
       Implementation / MCP Operator
                 ▼
                n8n
       Workflow / Execution Orchestrator
          ┌──────┼─────────┐
          ▼      ▼         ▼
     Knowledge  ERP DB   外部服務
       Base     /State   Calendar/Drive/API
          ▲
          │
          └──── 任務讀取 / 寫入 / 更新
```

## 知識循環

```text
任務 / 對話
   ↓
判斷是否需要既有 Context
   ├─ 不需要 → 直接執行
   └─ 需要
        ↓
   Knowledge Base 查詢
        ↓
   AI / n8n 執行
        ↓
      新結果
        ↓
是否形成可長期保存的知識 / 規則？
   ├─ 否 → 結束
   └─ 是
        ↓
     Candidate
        ↓
      WF-001
        ↓
   Human Approval
        ↓
   Knowledge Base
        ↺
```

## Authority 分工

- **使用者**：Final Human Authority；唯一最終核准者。
- **ChatGPT**：Architecture / Governance；定義 Contract、Ontology、Validation、Workflow 規則。
- **Claude**：Implementation Authority；依 Approved Contract 實作 n8n / MCP，不自行改治理語意。
- **n8n**：Execution Authority；執行已核准 Workflow，不自行做最終決策。
- **Google Sheets**：目前人類治理介面與 Source of Truth。
- **Knowledge Base**：保存可重用 Context、決策、知識與關係；不是所有任務都強制先經過它。

## 現階段順序

1. 先完成資料契約最小骨架。
2. 跑通 [[life-erp-wf001|WF-001 Contract Approval Pipeline]]。
3. 審查 [[life-erp-data-contract|Universal Contract / Node / Relation / Domain]]。
4. 再正式調整 [[life-erp-knowledge-base|Knowledge Base 架構]]。
5. 最後擴充 [[life-erp-n8n|n8n Workflow Runtime]] 與各 Life ERP 任務。

## 相關節點

- [[life-erp-data-contract|Life ERP 資料契約治理]]
- [[life-erp-wf001|WF-001 Contract Approval Pipeline]]
- [[life-erp-n8n|n8n 在 Life ERP 的角色]]
- [[life-erp-knowledge-base|Life ERP Knowledge Base]]

## 目前不要提前鎖死的東西

- Knowledge Base 最終一定用哪一個資料庫。
- Obsidian 資料夾最終長相。
- Vector DB / Graph DB 是否第一階段就需要。
- PostgreSQL schema 細節。

先固定「資料的語言與治理規則」，再決定 Storage Implementation。
