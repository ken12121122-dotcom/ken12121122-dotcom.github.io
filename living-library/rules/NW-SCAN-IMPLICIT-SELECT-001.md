---
rule_id: NW-SCAN-IMPLICIT-SELECT-001
version: 1.0.0
status: OWNER_LOCKED_CORE_INVARIANT
priority: CORE_P0_RELEASE_BLOCKER
owner_confirmed: 2026-10-22
scope: ALL_NEW_AND_EXISTING_SCANNABLE_WORLD_OBJECTS
change_type: DESIGN_RULE_AND_RELEASE_GATE_ONLY
parent_rule: NW-CORE-CONTRACT-001
implementation_reference: GitHub_PR_300_squash_944040e288606e66e0a5cdc2447481e8e21f716d
supersedes_conflicts_in:
  - 10_SYSTEMS/NW_TRUTH_EYE_SCAN_V2_長按契約與右側名稱清單_20261020_v0.2.md
  - 10_SYSTEMS/NW_TRUTH_EYE_DIRECT_HOLD_20261020_v0.3.md
  - manual_select_state_or_double_tap_required_by_any_future_code
---

# NW-SCAN-IMPLICIT-SELECT-001｜真實之眼「掃描即選取」最高優先級不可變規則（P0）

> **OWNER 於 2026-10-22 明確再次核准，並指出本規則已遭 AI 連續三次改錯。** 本規則是 `00_CORE` 的不可變操作契約，屬 `CORE_P0_RELEASE_BLOCKER`。對所有掃描器、3D 模型載入、卡片、動畫、法陣、UI 修改和任何新增物件均適用；**禁止因新增物件而更改掃描器交互語義**。不得自行降級至 10_SYSTEMS 或以工程師的「較合理操作方式」取代。

## 0. 唯一不可誤解的句子

**真實之眼掃描到物件，就等於選取該物件。已掃描物件不必再點擊、不必再標記、不必再按「選取」。**

此處選取是「暫時締約候選」，**不是**已建立卡片／契約，也**不代表自動封印**。這兩個層次不可混淆。

## 1. 固定操作流程

1. 普通 3D 物件進入世界：具穩定 objectId；即使看得到，未締約時直接點擊不發生選取、召喚、卡片化等互動（世界原本其他已授權物件功能仍保留）。
2. 開啟真實之眼：依當前世界中的真實 3D 幾何、掃描視角、方向、半徑與既定排除規則動態發現物件。不得改成物件種類白名單。
3. **掃描即選取**：所有當下符合掃描條件的可締約普通物件自動成為本次候選集合 `selectedObjectIds = scanResults.eligibleObjectIds`；範圍變更即同步更新選取集合；不設另一個人工選取狀態。
4. 長按任何一個正在被掃描的可操作候選物件 3 秒，啟動既有六芒星與確認流程；快照使用**當下掃描候選集合**，不要求先點一下。即使掃描到多件物件，仍可與**同一張既有空白卡**締約；絕不自動增造卡片。
5. 只有經玩家明確契約確認、通用安全與可還原性驗證，才允許持久化；取消、短按、拖動、逾時、模型缺失時保持 0 契約寫入。掃描動作不能單獨寫入所有權。
6. 締約後卡片化／實體化／查持有關係／解除**不再重新掃描**，遵守 `NW-CORE-CONTRACT-001`。

## 2. 禁止反例（Hard FAIL）

- ❌ 「掃描只是發現，之後要再選擇才能締約」。
- ❌ 「掃描後需要短按一下物件才算選取」，即使 UI 用勾勾或長按不變也不行。
- ❌ 因新增 GLB、雙界之門、書、家具、建築或任何未知物件，改成 `selectedScanIds`、`toggleSelected()`、按鍵選取、額外二次點擊或人工全選。
- ❌ 把臨時掃描候選誤當正式卡片契約，或把掃描更新結果寫回既有契約成員。
- ❌ 以舊的 `10_SYSTEMS` v0.2「掃描不等於選取」或 v0.3「單件預設僅包含長按物件」覆寫本規則。舊文件**保留作歷史紀錄**，其衝突段落失去現行效力。

## 3. 掃描範圍與複數物件

- 玩家要控制哪些物件被選取，應調整**掃描範圍、方向、角度、站位**。掃描器依幾何條件自動更新集合；不得強加另一套點選流程。
- 掃描到 1 件：候選 1 件。掃描到 N 件：候選 N 件（已締約、受保護或不可掃描者須依既有排除規則除外，並以 UI 明確呈現）。
- 右側名稱清單反映**掃描到的事實**，不能藉增設「已選 0 件」的獨立狀態阻擋契約。已有 UI 元件可以顯示件數，顯示方式不得改寫核心操作。
- 任何永久卡、角色、相機、控制節點、掃描器本體及受保護物件的排除條件仍有效，不能用「掃描即選取」繞過保護。

## 4. 每次修改《念動世界》前強制閱讀（Preflight）

**不論修改與掃描是否直接相關**（例如加一個 GLB、材質、場景、門、書、動畫、掃描範圍、卡片 UI 或修 bug），都必須在開始修改前依序讀：

1. GitHub `/AGENTS.md` 與 `/living-library/AGENTS.md`；
2. ChatGPT Library `/念動世界_GAME_DESIGN_KB/00_CORE/NW_CORE_OBJECT_CONTRACT_UNIVERSAL_INVARIANTS_v1.0.md`；
3. **本文件**，以及 repo 中的鏡像 `/living-library/rules/NW-SCAN-IMPLICIT-SELECT-001.md`；
4. 適用的原實作與相關測試基線（`truth-eye-world.js`、`scene-scan-discovery.js`、`scan-bundle.cjs` 等）。

在 PR 或交接紀錄寫明：`NW-SCAN-IMPLICIT-SELECT-001: READ / UNCHANGED / TEST_PASS`，缺任何一項不得聲稱驗收通過。只有 OWNER 重新明確指示修改這條 CORE 規則，才可以走具版本記錄的新審核；一般 Agent 不得擅自推翻。

## 5. 發布驗收條件（守門測試）

- Scanner ON，偵測到 1 件，`selected` 必須同步包含該 1 件；未有短按動作仍可長按三秒進入確認。
- Scanner ON，偵測到多件，`selected` 與本次有效掃描候選集合一致；長按其中一件時候選快照仍包含掃描到的有效集合。
- 新載入一個 GLB（例如 `scene.gate.obsidian.001`）之後，一般觸摸物件仍不會觸發契約；掃描它自動成為候選；不引入額外點擊選取。
- 切換相機、變更掃描範圍、移動角色之後，候選依現行掃描規則更新；**已提交契約不變**。
- 掃描但不長按：零持久化卡片／契約。長按未滿 3 秒或取消：零寫入。
- 檢查程式與瀏覽器測試均不得使用或要求 `selectedScanIds`、額外 `toggleSelected` 二次選取流程。
- 任一項 FAIL／NOT_TESTED → 不合併、不發布聲稱完成；保護玩家現有存檔與永久卡。通過 CI 也不等於手機實機已驗收。

## 6. 三次回歸的故障教訓

`2026-10-20` 曾存在不同設計草案：v0.2 需點選、v0.3 可免短按但預設單件；後續在 `2026-10-22` 新增普通 3D 門的流程中，工程端又把**動態掃描集合**改為**必須二次點選**的 `selectedScanIds`，使用者明確指正「掃描之後就等於選擇了」。核心原因是**設計來源衝突沒有先做優先級仲裁**，把新增物件與掃描器操作重設綁在一起。修復為：此規則升至 `00_CORE/P0`，置入 repo AGENTS 預讀清單、測試自動化與 PR preflight；後續新增物件只註冊普通場景物件，不重寫掃描選取語義。

## 7. 發布與狀態邊界

本文是 OWNER 已確認的**規則**；它不代表整個 `NW-CORE-CONTRACT-001` 持久化契約底層已全部符合 P0。最新程式狀態、CI、正式 Pages、手機驗收應獨立核對，不得從本文推斷已全部完成。規則升級本身不修改現有玩家資料、不變更傳送門位置或永久卡、不授權破壞性遷移。