# Life ERP 資料契約治理

> 狀態：`generated / candidate`，尚未人工核准。

## 目的

資料契約是 Life ERP 的共同語言。先定義「資料長什麼樣、誰能改、如何驗證、如何追溯」，再讓 n8n、AI、知識庫與未來資料庫共用同一套規則。

## 目前治理工作簿主線

```text
02_通用資料契約
   ↓
03_節點類別Registry
04_關聯類別Registry
05_領域Registry
06_狀態Registry
   ↓
07_變更集審查
   ↓
08_MD輸出映射
09_選項清單
10_契約索引
```

## 使用者主要要決定

### 03 Node Type
回答：系統裡允許存在什麼種類的節點？

目前候選：concept、method、product、demand、decision、process、evidence、resource。

### 04 Relation Type
回答：節點之間允許用哪些語意關係連接？

目前候選：belongs_to、requires、produces、depends_on、supports、supersedes。

### 05 Domain
回答：資料要用哪些主題維度分類？

目前候選：work、business、learning、finance、life、content_creation。

## 使用者只需審查，不必重建

- `02_通用資料契約`：Universal Contract 共用欄位骨架。
- `06_狀態Registry`：generated / reviewed / approved / rejected / on_hold / deprecated 等治理狀態。

## 系統優先管理

- `00_說明總覽`
- `07_變更集審查`
- `08_MD輸出映射`
- `09_選項清單`
- `10_契約索引`
- version / schema_version
- source_record_id
- source_modified_at / ingested_at
- Change Set / audit metadata

## 核心規則

```text
AI 可以提出 Candidate
AI 不可自行 Approved
來源不得捏造
缺資料要留下 unresolved_gaps
原始資料不覆寫
Contract 語意變更必須形成 Change Set
```

## 關聯

- [[life-erp-architecture|Life ERP 主架構]]
- [[life-erp-wf001|WF-001]]
- [[life-erp-knowledge-base|Knowledge Base]]
