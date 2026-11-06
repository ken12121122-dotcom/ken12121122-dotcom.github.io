# NW-RUNE-001｜法陣卡片化｜GitHub 實作計畫 v0.1

> `review_status: generated` · `implementation_status: planned` · `release_status: NOT_PUBLISHED` · `owner_approval_required: true`  
> A 規格依賴：`living-library/reviews/NW-RUNE-001_法陣契約與卡片化_開發規格_v0.1.md`  
> **不代表已完成儀式功能**。目前僅新增可執行的分工、檔案定位、測試與驗收門檻；不自動 merge。

## 0. 範圍與依賴

此次優先：**可互動地面法陣 → 第一次 3 秒長按 → 綁定最近普通卡與顯印 → 契約確認 → 吸卡封印 → 第二次 3 秒長按 → 確認卡片化 → 同卡復現**。

暫不做：實際 L2 唯一身分核准、永久收藏編號（包含 `ATLAS-0001`）、跨設備權威唯一、GEN2 私有 Skill 自動執行、原生 APK 或反向實體化。**保留既有 Three.js 升級卡預覽原始樣板，不擅自把它接成正式 L2**。

工程工作以 `main` 的現況為基準，而非把不同 Draft PR 未合併的候選邏輯當作已發布：`PR #250`（GBA 申請）、分支 `feature/atlas-l2-3d-relic-preview-20261016`（漂亮 3D 樣板）是**待審依賴**。

| 實際來源 | 目前責任 | 下一步如何重用 | 不可做的事 |
| --- | --- | --- | --- |
| `living-library/live/index.html` | 世界原點法陣 mesh、主渲染 loop、pointer、角色、漂浮卡 `Map` | 僅增加互動 `runeGroup`、事件路由與動畫 tick | 不改鏡頭自由旋轉、人物搖桿、底部法陣座標 |
| `living-library/live/object-craft.js` | `local.card.*` 與 `living-library-player-objects-v1` 單一快照 | 擴充可選法陣契約記錄與原子轉態／索引 | 不換卡 ID、不把 `cardNumber` 當 ID、不清空舊 objects |
| `living-library/live/card-placement.js`（來源需回讀核對） | 卡片相對玩家軌道位置 | 儀式中暫停單張卡的普通更新 | 不把地面法陣搬到人物周圍 |
| `living-library/live/atlas-runtime.js` | 卡片/卡冊 hit-testing 與拖放 | 把命中法陣事件在原輸入管線安全插入 | 不取代現有卡片拖放/書本交互 |
| `living-library/live/atlas-model.js`、`compendium-card.js` | 已存在卡片紋理和簡單卡 mesh | 封印印記加**獨立 3D overlay**，不覆蓋圖片 | 不重建普通卡、不破壞自行插圖 |
| `living-library/live/atlas-l2-threejs-preview.html`（僅候選分支） | 高階卡模型外觀樣板 | 等卡片化流程驗收後抽成模型工廠 | 不以 iframe/複製 HTML 取代 runtime |
| `living-library/live/atlas-controls.html` | WebGL 失敗時 2D 備援卡冊 | 新增只讀狀態與必要的簡化確認操作 | 不讓無 WebGL 模式改壞存檔 |
| `tests/browser/living-library-p0.cjs` | 主場景互動回歸 | 增加真實 pointer 長按與雙階段閉環案例 | 不繞過 UI 只改 localStorage 冒充端到端通過 |

## 1. 工作拆分：每個 PR 僅完成一個可驗證增量

### RUNE-01｜來源與資料錨點（P0，先做）
**產物**：`living-library/live/rune-contract.js`（純函數與候選 ID，尚未有網路/寫入），`tests/living-library-rune-contract.test.mjs`。

- 從現有地面網格構建可識別的 `runeGroup`，包含 `runeId`、場景錨點、名義顯示名稱；3D 法陣仍固定世界原點，不成為 `keeper` 子物件。
- 重新定位目前卡片 `record.id`、`group`、`containerId`、`revision` 等所有資料來源，建立來源表與狀態矩陣。
- `findNearestEligibleCard(runeWorldPosition, entries, config)` 是無副作用純邏輯；以 X/Z 世界座標、候選有效性、距離、並列／範圍外判斷，返回 `cardId/distance/reason`。
- **完成證據**：零張、一張、多張、等距、被回收／書內／儀式中／已綁定等測試通過；地面法陣固定不隨人物移動；原卡 ID 不變。
- **發布狀態**：Draft；不可自動合併。

### RUNE-02｜3 秒長按選取與法陣視覺（P0）
**產物**：`living-library/live/rune-interaction.js`、`rune-visual.js`（路徑以實作核對後可調）、`tests/living-library-rune-hold.test.mjs`。

- 只在 raycast 命中 `runeGroup` 時啟動長按計時；`performance.now()`、專屬 pointerId、pointer capture、有效手勢退出。
- 在法陣現有三圈 `torus` 與 22 枚 `runes` 追加選取動畫：圓形進度、亮度、旋轉和脈衝（避免新增常駐 UI 標籤）。
- **撞擊分析**：目前 `index.html` 的 `renderer.domElement` 先詢問 `atlasRuntime.pointerDown/Move/Up`，其他操作才交給人物/視角；新增 controller 必須有明確 input precedence，不得使手機搖桿、兩指縮放、原卡拖曳失效。
- **完成證據**：持續 3.0s 觸發一次；2.99s 取消、不拖成其他卡、失焦和第二手指取消；選錯物件不觸發法陣；WebGL 缺失時 2D 可用入口照常。
- **合併限制**：需要可見的 3D 動畫錄影/截圖及 OWNER 選取感驗收。

### RUNE-03｜最近卡自動綁定＋印記（P1）
**產物**：`living-library/live/rune-binding.js`（或併入純 `rune-contract.js` 命令）、`tests/living-library-rune-binding.test.mjs`。

- 3 秒選取完成時，篩選合法最近普通卡。**不設全世界任意最近卡**：原型建議 radius=3.0 世界單位、tie=0.15 單位，需手機實際校準。
- 綁定正本是 `runeId + cardId + expectedRevision`，不可用卡名或卡片表面手填編號。
- 顯示具有 `runeId` 來源的**可移除、可持久化**印記 overlay；印記不得遮住上傳圖片或黑色卡面文字，縮小到現有漂浮卡仍可辨認。
- 資料更新需在 ObjectCraft**同一個物件陣列快照**中提交法陣及卡片關聯（同一 `setItem`），可附事件序列和版本。
- **完成證據**：兩張競爭卡絕不雙綁、同步／重整後印記仍由正本恢復、重新點一次不新增綁定、錯誤及取消不覆寫舊資料。

### RUNE-04｜契約確認＋吸卡封印儀式（P1）
**產物**：`living-library/live/rune-ritual-animation.js`、`tests/living-library-rune-ritual.test.mjs`。

- 輕量確認 Dialog：`是否施放「法陣契約術」？`；法陣和卡名讀同一份資料，不新增額外固定螢幕按鈕。
- 點否留在 `bound`，卡片保持漂浮與印記；點是進入帶 token 的 `sealing`，對該張卡暫停一般 `updateFloatingCards`。
- Three.js 契約分鏡：多層法陣轉動、卡片印記亮起、使用原本 card mesh 按 cubic Bezier 飛至中心、縮小吸入、法陣封印完成。**不要呼叫回收卡片 API**；不從原始 objects[] 刪除媒介卡。
- 持久化提交 `sealed` 後，卡片仍存在資料中，但 renderer 刻意不顯示；退出／重整後恢復封印，不重新召喚一張。
- **完成證據**：儀式可以播放一次，不可兩次觸發；取消無消失；同一張卡的圖片/名稱/ID 保留；中斷回復可驗證；低效能/減少動態時可跳過飛行且狀態相同。

### RUNE-05｜第二次長按＋法陣→卡片（P1）
**產物**：延伸 `rune-interaction.js`、`rune-ritual-animation.js`、`tests/living-library-rune-cardification.test.mjs`。

- 對 `sealed` 原法陣長按 3 秒，**不再重搜卡片**，按畫面顯示 `是否將「{rune.displayName}」變成卡片？`。
- 確認 → `materializing` → 同一卡從法陣飛出，復現到既有內圈卡片軌道；世界法陣互動本體失效，位置錨點完整保存。
- 轉換前先鎖住相關世界/卡片操作；任何時間不得同時交互法陣和其投影卡。
- 復現的卡為 `cardified` 的**契約卡**，保留原 ID，不發 `L2` 身分與永久收藏編號；視覺等級另標示，避免讓原型金屬卡被誤判已認證。
- **完成證據**：獨立兩次長按、兩個確認、拒絕、重試、重開、錨點保存/失效、原卡浮動及卡冊相容；手機 3D 和無 WebGL 備援驗收分別記錄。

### RUNE-06｜通用化與耐故障（P2）
**產物**：`living-library/live/rune-transaction.js`（或責任明確的 ObjectCraft commands）、`tests/living-library-rune-recovery.test.mjs`。

- 提供`requestBind/confirmSeal/requestCardify/confirmCardify/recoverPending` 的同一類型化 API（名稱在實作時以實際語意調整）；每步**expected revision + idempotency key**。
- 處理 WebView 終止、動畫掉幀、重複 tap、二次進入、容量上限及儲存失敗的交易恢復；資料原子提交與外觀投影清楚分離。
- 模組化初始法陣為可重用模板 `runeTemplate`：後續法陣提供 `runeId/displayName/worldAnchor/meshRoot` 即可重用；不要複製貼上每座法陣的新 handler。
- **完成證據**：可使用合成資料跑第二座法陣且不互相干擾、同卡不可雙綁、斷電/重試不創建第二張卡、狀態機轉移全面測試。

### RUNE-07｜3D 靈核封印卡與 L2 介面（P3，嚴格後置）
**產物**：抽取 `atlas-l2-threejs-preview.html` 的幾何與材質成 `living-library/live/relic-card-model.js`，提供 `ordinary/candidate/certified` 三種視覺皮膚，而非新增卡 ID。

- 目前的漂亮樣板**維持獨立檔案、原封保留**。卡片化第一輪先確認動畫/雙向身分能成立，再逐步接 3D 輕量化材質。
- 只有**真正 L2 OWNER 核准**之後，才允許遊戲對該卡顯示「正式認證」及永久收藏；在此前可先展示設計級材質，但要有明確 `concept / candidate` 標示。
- 行動裝置需測 draw calls、貼圖記憶體、材質清理、WebGL 降級，避免把獨立樣板 26KB 原 HTML/另一個 renderer 生硬嵌進主場景。

### RUNE-08｜反向兌換（P4，待決定才排入開發）
**產物**：具原錨點校驗的 `cardified → restoring → sealed`，和首次 cardify 共用同一 transaction helper、同一圖像模型、同一 ID。

- 未核准前**不給出可操作按鈕**。正式啟用時，必須先保證原世界位置安全，再禁用卡片交互，最後讓法陣復現；沒有錨點就停止。
- 多裝置唯一性需另設權威同步/交易來源；`localStorage` 的單機關聯不能冒充全域唯一。

## 2. 共用模組 API 候選（先列契約，不寫死實作）

```ts
type RuneId = string;         // 原型 local.rune.origin；非永久收藏號
type CardId = string;         // 既有 local.card.*
type RuneState =
  | 'idle' | 'bound' | 'sealing' | 'sealed'
  | 'materializing' | 'cardified' | 'restoring';
type RuneAction =
  | {type:'HOLD_COMPLETED'; runeId:RuneId}
  | {type:'BIND_NEAREST'; runeId:RuneId; cardId:CardId; expectedRevision:number}
  | {type:'CONFIRM_SEAL'; runeId:RuneId; cardId:CardId; txId:string}
  | {type:'SEAL_ANIMATION_DONE'; runeId:RuneId; txId:string}
  | {type:'CONFIRM_CARDIFY'; runeId:RuneId; txId:string}
  | {type:'CARDIFY_ANIMATION_DONE'; runeId:RuneId; txId:string}
  | {type:'CANCEL_PENDING_UI'; runeId:RuneId};

interface RunePreview {
  runeId: RuneId;
  displayName: string;
  cardId: CardId | null;
  state: RuneState;
  hasWorldEntity: boolean;
  hasInteractiveCard: boolean;
  requiresOwnerApprovalForL2: true;
  permanentNumber: null;
}

interface RuneController {
  hitTest(pointerEvent: PointerEvent): RuneId | null;
  onPointerDown(event: PointerEvent): boolean;
  onPointerMove(event: PointerEvent): boolean;
  onPointerUp(event: PointerEvent): boolean;
  onPointerCancel(event: PointerEvent): void;
  tick(now: number): void;
  dispose(): void;
}
```

**資料處理與視覺分離**：`rune-contract.js` 決定合法性，`object-craft.js` 在同一存檔快照執行交易，`rune-interaction.js` 管 3 秒觸控，`rune-ritual-animation.js` 只更新 Three.js 視覺與發出動畫完成事件。任何一層不可自行發永久號、複製卡或繞過使用者兩次確認。

## 3. 測試計畫（每項均需明確 PASS/FAIL/NOT TESTED）

| 測試 ID | 內容 | 主要工具 | 目前 |
| --- | --- | --- | --- |
| R-T01 | 純函數距離、候選排除、兩卡並列 | Node `node --test` | NOT TESTED |
| R-T02 | 精確 3 秒觸控、不足時間與多指中止 | 模擬時鐘＋Playwright | NOT TESTED |
| R-T03 | 含卡片 revision 的唯一綁定、印記持久化 | Node/storage fixture | NOT TESTED |
| R-T04 | 第一次確認否與是、原卡吸入但不回收 | Playwright/WebGL | NOT TESTED |
| R-T05 | 第二次長按顯示法陣實名、同卡復現 | Playwright/WebGL | NOT TESTED |
| R-T06 | 同卡/同法陣不得複製、重複訊息冪等 | Node 模型 / property testing | NOT TESTED |
| R-T07 | 重整、儲存滿、資料不相容、動畫中止 | Node＋WebView | NOT TESTED |
| R-T08 | 人物/書環繞跟著人、地面法陣固定 | 原有 P0 browser + 新斷言 | NOT TESTED |
| R-T09 | 3D 卡片可旋轉、圖片保留、印記不遮擋 | Android 真機 / WebGL | NOT TESTED |
| R-T10 | WebGL 失敗時 2D 入口與資料完整 | 2D fallback + Node | NOT TESTED |
| R-T11 | 公開遊戲之書、卡片創造術、GBA/卡冊、GEN2入口完全不退化 | 專案完整回歸 | NOT TESTED |
| R-T12 | 不發 L2 身分、不配 `ATLAS-0001` 或卡片永久號 | 靜態＋命令級驗證 | NOT TESTED |

驗收證據分層：
1. 單元/狀態機 PASS ≠ 3D 操作已成功。
2. GitHub Actions PASS ≠ 手機／Android WebView 真機 PASS。
3. 使用者看到演出效果並確認 ≠ 物件已獲跨設備永久身份。
4. 必須記錄 commit SHA、測試 run ID、實際裝置驗收、來源存檔版本、回退點，才可宣稱對應階段完成。

## 4. 存檔版本與恢復策略

- **不要提前升級 `ObjectCraft.VERSION=1` 而使舊存檔直接拒讀**。採向後相容的 additive nullable 字段／可選 `objectType:'rune'`（需測試 `read()`、`listCards()`、備份 outbox 的兼容性）。
- 卡片契約更動使用單筆物件快照原子保存；新舊結構不相容時**拒絕並保留原資料**，不可默默清空。
- 動畫只是一種投影；恢復時由持久化 `state/txId` 決定是否補播放、回退或停止，不因刷新再創卡。
- 測試 fixture 必須使用合成 `local.card.*`，不匯出使用者真實卡片、圖片或私人知識進公開 GitHub。
- 反向兌換與權威註冊仍屬未來：目前持久化只能保證**同一本機快照**，不可宣稱全域唯一。

## 5. 分支與審核工作流

- 以每個工作包建立獨立候選分支，例如 `feature/nw-rune-01-core` ... `feature/nw-rune-05-cardify`；不要從已有未核准的大分支整包合併。
- 每次提交前 read back `main` 相應原始碼與目前在審 PR，避免主分支已更新導致覆寫。先更新純函數、後讀寫、更後 UI/Three.js；保留回退到原始 main 的能力。
- 各 PR **Draft** 開始，對照 before/after 與 AGENTS/UI-MANAGER-REGISTRY；先跑 Node、再 GitHub Actions、再單獨瀏覽器 3D 與 Android 手機驗收。未經 OWNER 指定「發布」不可 merge main。
- 不修改 `amin-vault/**`、Android APK、manifest、私有 GEN2 repo、正式圖鑑永久登錄及遊戲之書/系統之書正本。
- 影響已有玩家資料的寫入命令須先驗證一張合成普通卡完全走完閉環，沒有任何資料重複/遺失，再由 OWNER 決定是否擴展至真實卡。
- `NW-RUNE-001` 的 A 規格尚有 5 項 OWNER 待決定，**不得把本文規劃視為規則已核准**。

## 6. 交接與下一開發入口

> **下一輪只做 RUNE-01（先驗證法陣與最近卡定位）**：讀取 `AGENTS.md`、`living-library/AGENTS.md`、`living-library/UI-MANAGER-REGISTRY.md`、A/B 文檔及 main 的 `live/index.html` / `object-craft.js`。先製作不寫入玩家存檔的法陣穩定 ID、視覺命中/世界座標驗證、最近合法卡純函數與測試。完成後核對手機觸控路由，**再進 RUNE-02 長按動畫**。保留漂亮的 3D 樣板，勿發布、勿配號。

**本計畫只是工程拆分草案；RUNE-01～RUNE-08 的實作狀態均為 PLANNED，驗收均為 NOT TESTED。**
