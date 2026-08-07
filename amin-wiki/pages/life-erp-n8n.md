# n8n 在 Life ERP 的角色

> 狀態：`generated / candidate`。

## 定位

n8n 是 **Workflow / Execution Orchestrator**，不是 Knowledge Base，也不是資料契約的最終決策者。

## 正確關係

```text
Approved Contract
   ↓
Claude 依規格實作 Workflow
   ↓
n8n
   ├─ 讀取 / 寫入 Knowledge Base
   ├─ 更新 ERP State / Database
   ├─ 呼叫 Calendar / Drive / Gmail / API
   ├─ 執行提醒與自動化
   └─ 產生結果與執行紀錄
```

## n8n 不應自行做的事

- 自行新增 Ontology 類型。
- 自行修改 Approved Contract 語意。
- 自行把 Candidate 改成 Approved。
- 用流程方便性取代治理規則。
- 把所有資料硬塞進 Workflow 本身當永久知識。

## 未來任務分類

### A. 純執行任務

例如建立提醒、寫 Calendar。可不查 Knowledge Base。

### B. 需要 Context 的任務

先查 Knowledge Base，再執行 Workflow。

### C. 會形成新知識或新規則的任務

結果先成為 Candidate，經 WF-001 + 人工核准後才進正式知識層。

## 關聯

- [[life-erp-architecture|Life ERP 主架構]]
- [[life-erp-wf001|WF-001]]
- [[life-erp-knowledge-base|Knowledge Base]]
