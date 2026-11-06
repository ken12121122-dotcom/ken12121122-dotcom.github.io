# RUNE-CARD-IMPLEMENT-001｜法陣與卡片兌換 A→B GitHub 開發計畫 v0.1

**日期：** 2026-10-16
**類型：** B — 可執行的增量開發拆解；當前只建立文檔與候選資料，不寫入正式主場景
**來源：** RUNE-CARD-CONTRACT-001_初始法陣契約卡片化規格_v0.1.md
**審核：** generated；OWNER 必須先看過獨立 Three.js 審圖成果才能核准正式遊戲合併
**安全範圍：** 不更動 main、不發布網站、不修改既有卡片、不作 L2 配號

## 0. 現有資產與分支保護

1. 已存在的高品質 3D 卡面原型：
   - 分支：feature/atlas-l2-3d-relic-preview-20261016
   - living-library/live/atlas-l2-threejs-preview.html
   - tests/living-library-atlas-l2-visual.test.mjs
   - 狀態：已上傳到 GitHub 的**未合併候選分支**，不是正式遊戲卡片模型。後續不直接覆寫原型，先重用設計概念。
2. 舊 GBA 升級申請：
   - PR #250；分支 feature/atlas-upgrade-before-numbering-20261016
   - 狀態：Draft、尚未合併。這個提案目前僅能在卡片進入 GBA 後申請，缺少先綁定法陣的先決條件。
   - 處理：**保留不合併**，未來重構申請 gate 為「存在有效 sourceId/cardId 契約且完成卡片化」，避免繞過新玩法。
3. 現有正式運行環境：
   - 主場景 living-library/live/index.html
   - 地面法陣位於場景座標 (0,0) 基座；主要符紋 y 約 0.41；圈半徑 1.55、2.0、2.46。
   - 玩家漂浮卡位於人物相對座標，不在固定地面法陣上；物件由 LivingObjectCraft 管理。
   - 跨裝置唯一登錄與真正 L2 升級尚未完成，**不要在本需求搭便車加入**。

## 1. 交付架構與檔案責任

| 預定模組／來源 | 單一責任 | 寫入資料 |
| --- | --- | --- |
| living-library/live/rune-ritual-contract.js（新增） | 純資料規則與狀態轉換：eligible、nearest、assertTransition、idempotency、invariants | 不直接操作 |
| living-library/live/rune-ritual-visual.js（新增） | 法陣長按亮圈、卡面暫時/持久印記、光束、卡飛入、卡飛出、降低動畫效果版本 | 無 |
| living-library/live/rune-ritual-runtime.js（新增） | Three.js hit target、指標操作、3 秒 hold 計時、最近卡快照、modal 回調及動畫調度 | 透過 Craft 交易接口 |
| living-library/live/object-craft.js（修正候選分支） | 在同一版本化資料快照中保存來源錨點、契約 pair、phase、transactionId，實施雙向唯一性 | 是，限定本機 |
| living-library/live/index.html（小範圍候選接線） | 將原點法陣 3D mesh 分配 runtime id；事件路由先辨識法陣，且不干擾卡、書、人物、相機及搖桿；刷新可見實體 | 無直接業務決策 |
| living-library/live/card-creation.html（後期） | 顯示原卡的契約狀態與原卡名稱，不另建卡、不授予能力 | 仍由 Craft 負責 |
| living-library/live/atlas-l2-threejs-preview.html | **保持原檔不變**；後續把通用 3D 卡片結構抽成可重用元件並獨立審圖 | 無 |

### 模組接口（預定）

- findNearestEligibleCard({rune,cardSnapshots,maxDistance}) → {cardId,distance,revision} 或 null
- startHold({sourceId,pointerId,worldHit}) → token；updateHold(token,now) → progress；cancelHold(token,reason)
- previewBinding({sourceId,cardId,expectedRevision}) → 無副作用的暫時印記
- confirmBinding({sourceId,cardId,expectedRevision,ritualId}) → sealing intent（幂等）
- finishSeal({ritualId,expectedRevision}) → sealed；動畫中斷需 recoverSeal(ritualId)
- confirmCardify({sourceId,ritualId}) → cardifying intent
- finishCardify({ritualId}) → card_active，同一 card.id 進軌道
- restoreWorld({sourceId,cardId,ritualId}) → 以封存錨點恢復（留在後續階段）

**設計原則：** 引擎由 schema 驅動；「初始法陣」只是第一個註冊來源，未來新增任何法陣都提供 sourceId、displayName、anchor、effects preset、eligibility policy，不複製一套程式。

## 2. 分批實作與每一批獨立驗收

### R0｜建構差異基準與保存 3D 原型（不修改正式檔）
- Source audit：確認地面 mesh 邊界、卡片 mesh Map、card-placement orbit、觸控事件傳遞及 save/load 架構。
- 驗證高品質 3D 卡片原型分支可讀且未合併；保存固定 commit SHA。
- 記錄現有普通卡及法陣截圖，避免新特效遮蔽原本角色、GBA 或卡片。
- **完成標準：** 可穩定重現現有場景；無檔案被覆寫。

### R1｜純規則內核與自動化測試（不觸碰 3D）
- 建立 pure module 與候選 JSON contract（見 RUNE-CARD-WORKFLOW-001）。
- 測試：全部卡片資格檢查、真實世界座標排序、距離上限、ties、單一法陣/卡片互斥、replay idempotency、非法狀態拒絕。
- 模擬：外部時間、存儲失敗、stale revision、不認得版本，採 fail-closed。
- **完成標準：** Node regression PASS；不能透過狀態轉移產生第二張卡。

### R2｜「長按 3 秒 → 亮起法陣 → 最近卡印記預覽」獨立 3D 審圖
- 在獨立 Three.js 沙盒，不直接套進正式世界，重現地面法陣與至少兩張已命名普通卡（使用假的 ID）。
- 捕捉 raycast 命中 mesh 的 pointerdown；優先於原本的相機/搖桿/書/卡指標處理，僅直接點到法陣才啟動。
- 3,000 ms 進度圓圈、取消、雙指與 touch-action 隔離、按住完成只選一次，最近卡獲得暫時符印。
- **完成標準：** 2.99 秒不能觸發；滿 3 秒一次觸發；卡片不丟、不新增、不更動編號；手機可操作。

### R3｜契約確認與「同一卡片飛進法陣」動畫
- 確認視窗只兩個明確選項；使用 sourceName 與 card.title，不硬編碼。
- 產生 ritualId，透過 Craft 在**同一快照**提交可恢復的 sealing intent，再由 visual module 執行卡片飛入/縮小/消失。
- 被吸收卡在資料中仍保留 ID，但從軌道渲染/碰撞中排除；法陣出現持久契約印記。
- **完成標準：** 拒絕不變更，確認後不重複；切頁、刷新、資源暫停後仍能判定一致契約狀態。

### R4｜封印法陣第二次長按 → 法陣卡片化
- sealed 狀態才接受第二次 3 秒 hold；直接顯示「是否將〈sourceName〉變成卡片？」
- 同一筆契約切換至 cardifying，動畫讓原卡從法陣飛出；關閉法陣的活躍射線命中（世界平台仍可存在）。
- 顯示 cardified 但 **not L2**，繼續沿用原 card.id / title / image。
- **完成標準：** 翻轉後卡和來源不可同時可操作；reload 後只有一個活躍形態；無配號。

### R5｜反向兌換與恢復機制（先內核，待 OWNER 核准 UI）
- 綁定不可解除或更改對象；如果需要重新綁定，必須有獨立受控功能而不是清空資料。
- 在同一契約上保存 world anchor；設計卡 → 原位置法陣的逆向流程（不創建新實體，不替換 ID）。
- 加入 transaction recovery：本機資料讀寫失敗時不播放成功結算；重試不得重複添加模型。
- **完成標準：** 不需要有第二份 card 或 rune；任意中斷後資料與可見形態仍一致。

### R6｜整合到念動世界、回歸與發行閘門（需獨立核准）
- 前面沙盒全部通過後，才接入 living-library/live/index.html；切勿整頁重寫。
- 沿用既有 card placement、煙霧與相機姿態；隱藏的卡從所有拾取/拖曳/移書操作中排除。
- 兼容既有 main JS、2D fallback、reduced-motion、GBA 讀卡與既有 public.gaming.atlas 規則頁。
- OWNER 審核手機實機效果、兩次 3 秒流程、刷新/中斷回復，再進入 merge / 正式發布決定。
- 正式 L2 與永久編號開發 **R6 之後另開工作，不屬本 PR**。

## 3. 自動回歸矩陣（各批均在候選分支）

| Case | 預期 |
| --- | --- |
| 單卡、兩卡、同距、無卡、卡太遠 | 最近卡選取可預期；無可用卡停止 |
| card 未命名、recycled、already sealed、in-book、正在拖曳 | 不能成為契約媒介 |
| 按住 2990ms、滿 3000ms、移動超容差、兩指、pointercancel、blur | 只有合格操作觸發一次預覽 |
| 預覽按否、按是前來源/卡片版本已改、連點是 | 無副作用／阻擋／冪等 |
| 施術中 reload、WebGL context lost、storage quota exceeded | 安全復原或停止，無雙重可見 |
| 首次契約完成 → 第二次長按 → 卡片化 | 使用相同 card.id；世界來源與實體卡排他 |
| 保存/恢復的來源錨點與人物移動 | 固定 world anchor，與 avatar orbit 分離 |
| 原卡圖、名稱、圖片及空編號欄位 | 不丟失，不填永久號，不假核准 L2 |
| 2D 後備、行動裝置 30fps、reduced-motion | 仍可取消、驗證；效能不足不導致重複觸發 |

**證據輸出：** 每次 PR 需保存 Node 及 Playwright 結果、更新的候選 state schema、使用者核准決策記錄、觸控重播記錄、失敗回復測試；重要處理標記「程式已實作 / 測試 PASS / OWNER 已核准 / 已發布」四個不同欄位，不可混用。

## 4. 建議 PR 切分（每個都可獨立退出）

- PR-R1｜純契約資料內核、JSON schema、Node tests；無 UI、無正式場景變更。
- PR-R2｜獨立 Three.js 沙盒的法陣 3 秒選取及最近卡印記；不發布。
- PR-R3｜契約確認 + 飛入封印動畫 + 原子保存與斷線恢復。
- PR-R4｜第二次長按、同一卡片飛出、世界法陣停用。
- PR-R5｜逆向轉換接口及異常重放；按審核需求拆分。
- PR-R6｜正式遊戲的最小化整合與上線驗收（單獨 OWNER 核准）。

每個 PR 預設 draft，必須查驗分支的 GitHub Actions 成功後才可標記驗證 PASS；只有使用者明確批准合併，才能進正式版本。後續工作不因上一個 draft 自動發布。

## 5. 不可碰觸項目及後續 L2 掛接

- 保護 feature/atlas-l2-3d-relic-preview-20261016 中已建立的漂亮 Three.js 卡片；不要把它直接替換成舊 2D 卡。
- 保護 main 的 ObjectCraft v1 卡片 ID、私人存檔、cardBook 與 GBA 導覽，不以「卡片化」名義清除資料。
- 先不修改遊戲之書、系統之書、GEN2 技能、永久圖鑑 ID、永久收藏編號。
- 先不以三位數 / 四位數容量作為卡片化的前置條件。
- 後續 L2 申請入口在 ritual state = card_active 且驗證全部通過時才解除封鎖；美術上可預覽高階外觀，但 UI 必須區分「概念展示」與「正式已認證」。

## 6. 本輪 A→B 的完成定義

本輪只輸出經過 source audit 的正式候選規格、版本化開發計畫與 JSON 技能契約；**不寫任何實際遊戲行為、不把任何草稿 PR 合併到 main、不發布 GitHub Pages**。

本規格可獨立用於下個對話承接：先讀 CONTRACT-001、IMPLEMENT-001、WORKFLOW-001 及既有獨立立體卡 HTML，再依 R1 開始。
