# 萬物圖鑑 P1｜唯一圖鑑本體 ID 候選契約 v0.1

- project_id: `NW-ATLAS`
- issue_id: `ATLAS-002`
- card_id: `public.gaming.atlas`
- review_status: `generated`
- implementation_status: `candidate_readonly_validator`
- owner_approval_required: `true`
- release: `not_approved`

## 一、目的與邊界

讓「正式功能卡片」、「既有 3D 容器」、「未來唯一圖鑑登錄身分」三者分離。這是 P1 第一個**可測試、唯讀、可撤銷**的技術單元，而不是核准編號或建立登錄資料。

| 層次 | 現有或候選識別 | 狀態 |
| --- | --- | --- |
| 功能卡片 | `public.gaming.atlas` | GitHub 公開卡片正式 ID，維持不變 |
| 3D 本機卡片容器 | `local.atlas.compendium` | 現有程式容器 ID，非永久圖鑑身分 |
| 唯一圖鑑對外編號 | `ATLAS-0001` | **未經核准的示意格式，不可視為配發** |
| 權威唯一登錄號 | `null` | 未設立可信登錄來源，禁止推定 |

## 二、單一責任技能草案

```yaml
skill_id: SKILL-NW-ATLAS-ID-CHECK-001
skill_name: 萬物圖鑑候選 ID 格式檢查
purpose: 唯讀檢查示意格式並輸出機器可讀缺口
review_status: generated
version: 0.1.0
required_inputs:
  - candidateId
optional_inputs:
  - knownIds
decision_rules:
  proposed_pattern: "ATLAS-[0-9]{4,}, 數字尾碼不為全零"
  notes:
    - 格式僅是候選，不構成正式規則
    - 使用者提供的 knownIds 僅是測試資料，不是權威登錄
prohibited_actions:
  - 配發或保留永久編號
  - 修改卡片或 3D 圖鑑
  - 寫入 localStorage、GitHub 或遠端服務
  - 將示意格式聲稱為正式核准
output_schema: nw-atlas-identity-check-v1
human_approval_required: true
can_modify_source: false
can_generate_draft: true
can_make_final_decision: false
audit_log_required: true
stop_conditions:
  - 輸入缺少或格式錯誤
  - 權威登錄尚未確立
  - 人工核准紀錄缺失
```

## 三、狀態定義

`checkCandidate({candidateId,knownIds})` 回傳固定欄位 `schema`, `project_id`, `card_id`, `container_id`, `candidate_id`, `proposed_pattern`, `format_status`, `conflict_status`, `review_status`, `identity_issued`, `may_register`, `requires_human_review`, `source_records`, `unresolved_gaps`。

即使符合候選格式，`identity_issued` 與 `may_register` 必定為 `false`；`review_status` 必定為 `generated`。無權威資料時 `conflict_status=not_checked`；只給測試名單時不能推論全域唯一。

## 四、需由 OWNER 定案（阻擋配號）

1. 對外正式命名格式、第一個可配發編號、是否保留編號與衝突處理。
2. 允許誰批准唯一圖鑑本體、是否僅存在一個權威圖鑑，以及對應世界錨點。
3. 哪個資料庫提供不可重用的全域唯一性、版本與可追溯的核准記錄。
4. 卡片升級成 L2 前的身份審核接口、數據驗證及回退方式。
5. 是否將操作規則納入遊戲之書、身分與唯一性規範納入系統之書。

在上述項目確認以前，**不得**依這個候選格式在 `ObjectCraft` 或 `atlas-state.js` 加入任何真實配號。

## 五、來源與驗證

- `living-library/live/public-catalog.js`：正式功能卡 ID。
- `living-library/live/atlas-state.js`：本機圖鑑容器 ID、十個槽位。
- `living-library/live/atlas-rulebook-review.html`：唯一圖鑑規則、`ATLAS-0001` 尚待核准。
- `living-library/live/atlas-development-progress.json`：ATLAS-002 進度與審核責任。
- `tests/living-library-atlas-identity-contract.test.mjs`：候選格式、相似 ID、重複、錯誤名單、不可核准/配發及無副作用測試。

所有輸出仍為候選草稿，不會修改或覆寫任何來源。與既有本機卡片的真實一致性驗證，待日後獨立技能實作及 OWNER 驗收。
