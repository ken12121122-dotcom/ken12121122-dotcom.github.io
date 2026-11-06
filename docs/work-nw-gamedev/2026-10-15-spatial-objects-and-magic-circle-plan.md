---
project_id: NW-SPATIAL-OBJECT-SYSTEM-001
parent_skill_id: WORK-NW-GAMEDEV-001
version: 0.1.0
status: CANDIDATE
review_status: generated
implementation_verified: false
owner_decision: design_and_plan_inclusion_only
date: 2026-10-15
---

# 念動世界｜法環之書、動態錨點與萬物收納擴充計畫（候選）

> **增量計畫，不取代既定 WORK-NW-GAMEDEV-001 / P0～P7。** 本檔僅紀錄本次 OWNER 需求與實作切片；不是新 Work 母流程、不是已完成工程、不是授權發版。對應 L1 理想保存位置：`/念動世界_GAME_DESIGN_KB/30_BUILD/WORK_GAME_DEV_SKILL/WORK_EXTENSION_SPATIAL_OBJECTS_v1.2.md`；Library 是否完成寫入必須獨立驗證，**不能以本公開 GitHub 文件冒稱已同步 Library**。

## A. 正式記錄的 OWNER 決策

1. 現有地面魔法陣外觀將改以 **SVG 即時建模規格 → Three.js 地面紋理** 產生，可保存、預覽、切換與保留歷史，原圖作渲染失敗時 fallback。
2. **不另設「魔法陣創造術」按鈕**。直接點擊 3D 地面法陣：激活法陣、隱藏《法環之書》由陣中飛出、開啟管理頁；關閉後**同一本書飛回原陣**。法環之書不得冒充已授權 GEN2 技能或占玩家空白書名額。
3. 法環之書管理畫面提供目前法陣、設計切換／預覽、收藏與版本／使用歷史；保留極簡純圖示操作、aria-label 與手機觸控。
4. **通用化召喚與回歸**：抽出卡片創造後既有 3D 煙霧／飛行弧線／插值／計時／清理；書本、卡片、工具、工作台等按身分與錨點共用，不複製兩套效能昂貴的實作。
5. 撤銷「法陣永遠固定於世界原點」的永久限制。當前第一座地面陣**仍維持舊版本的位置行為**（不跟人物走），但未來父空間可遷移，城堡移動時其所有合法附屬法陣與物件可共同平移。
6. 長期允許**反向封印**：魔法陣可縮回《法環之書》；法陣封印之後，不得再成為那本書的外部回歸目標，應改轉玩家背包或其他合法的存在錨點。重新展開時恢復其法陣身分及空間位置。
7. 移動城堡仍遵守玩家所有權、建造來源、技能熟練度與空間容量限制。新手村／道路／公共 NPC／公共設施等受保護資產不可任意收納。

## B. 與既有技術對接，避免平行系統

| 既有來源 | 證實可用作候選基礎的能力 | 仍待實作 |
| --- | --- | --- |
| `living-library/live/index.html`：`startCardSummon`, `createCardSmoke`, `updateCardSmoke`, `removeCardSmoke` | Three.js sprite 煙霧、卡片從來源飛到獨立軌道的插值與拋物線弧線 | 抽出 `OBJECT-SUMMON-RETURN-001` 共用 API；支援逆向、移動錨點、取消 |
| `living-library/live/index.html`：`bookMesh`, `updateOrbit`, `rebuild` | 同一本 3D 書移動、書封面／書脊文字材質 | 法環之書的獨立隱藏書生命週期（不加入一般環繞圈） |
| `living-library/live/index.html`：`pick(e)` | 現有卡／書／人物 3D Raycast | 魔法陣實體 hit target，避開人物搖桿／鏡頭旋轉誤觸 |
| `living-library/live/card-creation.html` | 2D CSS 召喚頁外觀 | 不能拿它冒充真正 3D 動畫或手機已驗收 |
| `living-library/live/object-craft.js` | 玩家自創 card/book 穩定 ID 與本機存檔 | 新法陣資料分表／版本遷移，不覆寫現有存檔 |
| `RUNE_ENGINE_DELIVERY_PLAN_v0.4.md` | P0～P7 發布閘門、ActionRegistry／安全測試原則 | 新功能以子切片掛入既有 Gate，**不建立 P8** |

### 既有 ID / 建議新 ID

- 保留 `WORK-NW-GAMEDEV-001`、`WF-WORK-NW-GAMEDEV-001` 及 `KW-GAME-MEMORY-PALACE-20261014`，不建立第二份母計畫／重複遊戲設計卡。
- 新增子專案 `NW-SPATIAL-OBJECT-SYSTEM-001`。
- 新模組候選：`SVG-GAME-BRIDGE-001`、`MAGIC-CIRCLE-PORTAL-001`、`MAGIC-CIRCLE-LIB-001`、`OBJECT-SUMMON-RETURN-001`、`OBJECT-ANCHOR-001`。
- 原本 `NW-AGENT-MAGIC-BRIDGE-001` 是 v1.1 獨立子專案，保持獨立；**本次不啟用後端 Agent 或同步**。

## C. 空間與收納協議的核心不變條件

```json
{
  "objectId": "system.magic-circle.primary",
  "objectType": "magic_circle",
  "parentSpaceId": "space.library.main",
  "localTransform": {"position":[0,0.42,0],"rotation":[0,0,0],"scale":[1,1,1]},
  "containerId": null,
  "summonAnchorId": "anchor.circle.primary",
  "activeDesignId": "circle.design.default",
  "lifecycleState": "idle",
  "revision": 1
}
```

- `objectId` 是永久身分；`parentSpaceId` 是所在空間、`localTransform` 相對父空間、`containerId` 表示實際收納容器、`summonAnchorId/returnAnchorId` 只定義動畫起迄，不互相混用。世界座標由父空間鏈計算。
- 建立無環的空間／容器 DAG 或樹，禁止直接或間接自行收納，禁止一物件同時有兩個權威容器。
- 收納／展開先檢查所有權、技能、容積、protected 世界區域及目標錨點，再進行有中斷恢復的交易性狀態轉移；相依物件不可孤兒化，原本書／卡 ID 不變。
- 法陣封印入書後，普通「飛回法陣」動畫落點失效；應改走合法玩家持有錨點。開書管理與封印模式是**兩種不同操作**。

## D. 增量實作順序（不搶正在開發的卡片創造術）

| 切片 | 交付／驗收證據 | 既有 Gate |
| --- | --- | --- |
| **S0** 實測基線 | 查當下 main SHA、卡片煙霧版本、3D 地面陣、書本 mesh、pointer pick、手機情況及 rollback；舊功能回歸 | P0 |
| **S1** SVG 外觀橋接 | 向量參數轉安全材質／紋理、快取、禁外部 script/URL、舊圖 fallback；原型與 WebGL 比對 | P1/P2 |
| **S2** 法環之書閉環 | 點法陣→同一隱藏書飛出→開頁→關閉→飛回法陣；重複點不複製書、取消不鎖視角 | P1/P3/P7 |
| **S3** 法陣圖鑑 | 至少兩個法陣配方：預覽、保存、切換、版本／使用歷史，重載保持；不改原 card/book 存檔 | P2/P7 |
| **S4** 動畫抽象 | 卡片動畫穩定後抽出共用飛行／煙霧 API；回歸證實舊卡片召喚沒變 | P1/P3/P7 |
| **S5** 動態錨點 | 物件跟隨合法父空間／可移動平台，召喚與移動同時發生可恢復 | P1/P3/P7 |
| **S6** 反向封印 | 同一個法陣收進書、重新展開、保存回讀；無效回歸錨點與循環封印被拒 | P1/P2/P3/P7 |
| **S7** 移動城堡依賴 | 玩家自建城堡、合法附屬物件與封印的空間轉換，先規畫可驗收切片 | 未來 P1/P2/P3/P7 |

**近期優先 S0～S3；S4 等待卡片創造動畫穩定，再重構共用底層；S5～S7 是長期候選。** 目前 S0～S7 均未由本 PR 實作或驗收。

## E. 最低回歸與發布限制

- `same_book_id`、`roundtrip_return`、`tap_circle_not_avatar`、`no_duplicate_hidden_book`、`svg_fallback`、`switch_reload`、`moving_parent_transform`、`nested_cycle_rejected`、`seal_unseal_transaction`、`reduced_motion`、`android_webview`。
- 不破壞現有 GBA、GEN2 權限、搖桿、自由 360° 鏡頭、漂浮書及卡片軌道、地面初始定位、書架、卡片身分、本機存檔、行動裝置效能。
- 本 PR 僅文件候選；不修改任何正式 Runtime／Android。未見真實 CI、staging、Pages、手機時，各別標 `NOT_TESTED`。功能實作分支不可推 main，Owner Gate 保留。

## F. 給 Work 的下一輪接手語

> 接續 `WORK-NW-GAMEDEV-001 / NW-SPATIAL-OBJECT-SYSTEM-001`：先讀 P0～P7 母計畫、Library v1.0/v1.1 與本候選擴充。查最新 main/PR 的卡片創造煙霧動畫與手機實機狀態，先 S0 確定回歸基線，再循序選 S1～S3 的最小可驗證切片。不要搶修改正在開發中的卡片動畫；S4～S7 待相依能力與 OWNER Gate。不得把本文件當作已部署功能。
