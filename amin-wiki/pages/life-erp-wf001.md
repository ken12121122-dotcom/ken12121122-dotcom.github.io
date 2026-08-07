# WF-001 Contract Approval Pipeline

> 狀態：`generated / candidate`，尚未人工核准。

## 角色

WF-001 是 Life ERP 的第一條治理 Workflow。它不是自動核准器，而是「契約進正式系統前的審查閘門」。

```text
Contract Candidate
   ↓
normalize_input
   ↓
必填欄位驗證
   ↓
Schema / Enum 驗證
   ↓
來源驗證
   ↓
版本驗證
   ↓
與上一 Approved 版本比較
   ↓
Change Set
   ↓
Governance Validation
   ↓
Approval Gate
   ├─ generated / reviewed / on_hold / rejected → STOP
   └─ approved
        ↓
MD / YAML / JSON / JSON Schema
        ↓
IMPLEMENTATION_HANDOFF
```

## 核心保護

- 不允許 AI 自動把資料改成 `approved`。
- 不允許驗證失敗後繞過 Validator。
- 不允許補造來源或缺失欄位。
- 不允許覆寫原始 Sheet。
- 重大語意變更必須回到人工審查。
- Change Set 預設從 `generated` 開始。

## Contract Index 的作用

`10_契約索引` 定義一份 Contract 的 Envelope，包括：

- contract_id
- contract_type
- source_sheet
- record_granularity
- version
- schema_version
- review_status
- source tracking 規則

避免把「Contract 裡的一列」錯當成「一整份 Contract」。

## 執行權限

- Architecture / Governance：使用者 + ChatGPT
- Implementation：Claude
- Execution：n8n
- Final Approval：使用者

## 關聯

- [[life-erp-architecture|Life ERP 主架構]]
- [[life-erp-data-contract|資料契約治理]]
- [[life-erp-n8n|n8n Runtime]]
- [[life-erp-knowledge-base|Knowledge Base]]
