# amin-wiki workflow extension

本文件補充既有 `amin-wiki` 架構的使用方式。它不取代 `CLAUDE.md`，也不更名、不移動任何既有資料夾或檔案。

## 目的

保留目前已建立的：

- `raw/`
- `pages/`
- `index.md`
- `log.md`

同時補足使用者原本期待的資料生命週期：

1. 投入資料
2. 清洗與判讀
3. 形成知識節點
4. 建立節點關係與圖譜

## 與現有結構的對應

### 1. `raw/`：來源與投入層

既有規則仍維持：`raw/index.md` 是權威來源索引，不重複貼上外部正本。

未來可在不改名的前提下，於 `raw/` 增加新的投入紀錄檔，用來登記尚未完成知識化的資料，例如：

- 新增文件路徑
- 對話摘要
- 網頁或研究來源
- 待驗證說法
- 尚未拆解的筆記

每一筆投入至少應記錄：

```yaml
source_id: SRC-YYYYMMDD-001
source_type: document | conversation | webpage | note | code
source_reference: 待補來源或實際路徑
received_at: YYYY-MM-DD
status: received | processing | processed | rejected
contains_unverified_claims: true | false
notes: 文字說明
```

`raw/` 保存來源身分與追蹤資訊，不把未驗證內容直接當成正式知識。

### 2. 清洗與判讀：以狀態欄位表示，不新增或更名既有資料夾

目前架構沒有獨立的清洗資料夾。為避免破壞既有結構，清洗階段先用文件 Metadata 與紀錄狀態表達。

建議中介資料包含：

```yaml
processing_status: extracted | normalized | deduplicated | source_checked | ready_for_knowledge
source_ids:
  - SRC-YYYYMMDD-001
claims:
  - claim_id: CLM-001
    statement: 待整理的敘述
    verification_status: verified | conflicting | unverified
    evidence:
      - source_id: SRC-YYYYMMDD-001
        reference: section_or_line
unresolved_gaps:
  - 待補資料
```

清洗階段必須做到：

- 拆分不同主題
- 去除重複內容
- 保留來源
- 區分事實、推論與建議
- 標記矛盾、過時與待確認內容
- 不因資料不足自行補造

清洗結果未達 `ready_for_knowledge` 前，不應直接宣告為正式知識節點。

### 3. `pages/`：正式知識節點層

`pages/` 維持現有名稱與用途，作為可閱讀、可引用、可互相連結的知識頁面。

建議每一篇新頁面逐步採用以下 Front Matter：

```yaml
---
id: release-process
title: 發布流程
node_type: architecture | process | issue | reference | decision | concept
status: draft | reviewed | active | deprecated
source_ids:
  - SRC-YYYYMMDD-001
tags:
  - android
  - release
relations:
  - architecture
  - known-issues
contains_warning: true
updated_at: YYYY-MM-DD
---
```

規則：

- `draft` 代表候選知識，不等於已核准。
- `reviewed` 代表已完成內容檢查。
- `active` 代表目前可使用的知識節點。
- `deprecated` 代表保留歷史但不應當作現況。
- `⚠️ 矛盾`、`⚠️ 已過時`、`⚠️ 待確認` 必須原樣保留並在圖譜中可辨識。

### 4. `index.md`：人工可讀的入口與目錄

`index.md` 維持目前角色，不改名、不搬動。

後續可逐步補充：

- 頁面類型
- 狀態
- 主要來源
- 重要關聯節點
- 是否含警告

`index.md` 是人類與聊天模型的第一層導航，不取代正式圖譜資料。

### 5. `log.md`：不可覆寫的操作紀錄

`log.md` 維持 append-only。

除了既有 `ingest | query | lint`，未來可在標題或內容中記錄：

- 收到新來源
- 清洗完成
- 建立節點
- 合併重複節點
- 標記矛盾
- 人工審查結果
- 節點淘汰或取代

不得刪除舊紀錄來掩蓋過去判斷。

## 節點與關係圖譜

第一版圖譜可直接解析現有 Markdown 連結：

```markdown
[發布流程](./release-process.md)
```

未來也可接受 Obsidian 風格 Wiki Link：

```markdown
[[release-process]]
```

建議關係類型：

```yaml
relation_type:
  - references
  - depends_on
  - conflicts_with
  - supersedes
  - derived_from
  - related_to
  - implements
```

若沒有足夠來源判定關係類型，應使用 `related_to` 或標示待確認，不得虛構強關係。

## 建議流程

```text
新資料或新來源
↓
登記於 raw/ 的來源索引或投入紀錄
↓
抽取、清洗、去重、來源核對
↓
資料不足 → 保留 unresolved_gaps，不進正式節點
↓
產生或更新 pages/ 知識節點
↓
更新 index.md
↓
追加 log.md
↓
網頁重新載入並重建節點圖譜
```

## 相容性與邊界

- 不更名或搬動既有 `raw/`、`pages/`、`index.md`、`log.md`。
- 不覆寫其他貢獻者已建立的內容。
- 新規則採漸進式導入，舊頁面仍可正常使用。
- 不修改 `amin-vault/`。
- 不修改 `android-native/`。
- 不修改 `native-release-manifest.json` 或 `runtime-manifest.json`。
- 所有 AI 產出先視為候選草稿；涉及正式判斷時仍需人工審核。

## 第一階段實作建議

在不破壞現有結構的前提下，下一階段只需要：

1. 讓 Viewer 讀取現有 `index.md`、`raw/index.md`、`pages/*.md`、`log.md`。
2. 解析標準 Markdown 連結，建立第一版節點與邊。
3. 將含 `⚠️` 的節點標示為警告狀態。
4. 保留未來解析 Front Matter 與 `[[Wiki Link]]` 的擴充接口。

這樣可先看到現有知識庫的關係圖，再逐步導入更完整的投入與清洗流程。
