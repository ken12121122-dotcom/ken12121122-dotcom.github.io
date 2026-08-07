# Life ERP Knowledge Base

> 狀態：`generated / candidate`。Knowledge Base 最終 Storage Implementation 尚未定案。

## 定位

Knowledge Base 保存可重用的 Context、決策、知識與節點關係；它不是每個任務都必須經過的中繼站。

## 三種任務路徑

```text
A. 純執行
Task → Contract / Workflow → n8n → 外部服務

B. 需要 Context
Task → 查 Knowledge Base → AI / n8n → Result

C. 形成新知識
Task → Result → Candidate → WF-001 → Human Approval → Knowledge Base
```

## Contract-first 原則

現在先固定知識的「語言」，再固定知識存放技術。

```text
Universal Contract
   ├─→ Markdown / Obsidian
   ├─→ JSON
   ├─→ PostgreSQL Row
   ├─→ Vector Index
   ├─→ Knowledge Graph
   └─→ AI Context
```

只要 Contract 穩定，底層 Storage 可以替換或並存，不需要重新定義所有知識。

## 現在 vs 未來

### 現在

- Google Sheets：人工治理 / Source of Truth。
- GitHub Markdown：手機端知識圖可視化與文件節點。
- WF-001：第一個正式治理 Workflow 候選。

### 下一階段

WF-001 跑通、Universal Contract 與 Registry 審查完成後，再決定：

- Obsidian 最終資料夾與 Frontmatter。
- PostgreSQL / ERP State schema。
- 是否需要 Vector DB。
- 是否需要獨立 Graph DB。
- n8n 的 Knowledge Read / Write Workflow。

## 不要混淆

- n8n = 流程編排。
- Knowledge Base = 長期 Context / Knowledge。
- Data Contract = 資料共同語言。
- WF-001 = Contract 治理閘門。

## 關聯

- [[life-erp-architecture|Life ERP 主架構]]
- [[life-erp-data-contract|資料契約治理]]
- [[life-erp-wf001|WF-001]]
- [[life-erp-n8n|n8n Runtime]]
