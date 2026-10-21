# NW-SURFACE-BRIDGE-001｜《念動世界》× Surface Lab 005 雙視角整合

- Task-ID: NW-SURFACE-BRIDGE-001
- Change-Class: C2 (3D 場景整合候選)
- Base-SHA: `e0364126ceb20b405d15c02a79159a91e11c2d49` (`main` 查核於 2026-10-21)
- Status: **generated / NOT APPROVED / experimental**
- Scope: `living-library/experiments/nw-surface-bridge/**`、`tests/nw-surface-bridge.test.mjs`、本文件；不修改正式 runtime
- Reuse-Check: 搜尋現存分支 `surface`、`fusion`、`survival`、`creative` 及開放 PR；未找到同目的「Surface Lab 005→念動世界」runtime 整合分支／PR。歷史 `work/nw-gamedev-p0-20261014` 是不同的舊分支整合工作，本案不覆寫或合併其老碼。
- Source A: `living-library/live/index.html`；Three.js 3D 圖書館正式入口（此任務唯讀）。
- Source B: ChatGPT Library 既有 `SURFACE_ANCHOR_3D_WORKBENCH_v0.5_全模式物件鎖點.html`（原始檔不改）；該工作台是 **Canvas 2D 自行投影的 3D 建模操作**，不是 Three.js renderer，資料 key 為 `surface_lab_005_v05`、匯出 JSON 是 `version:4`、`unit:"cm"`、`surfaces`、`anchors`。
- Source C: 根目錄 `AGENTS.md`、`living-library/AGENTS.md`、`living-library/public-capabilities.json`，特別保留 NW-CORE-CONTRACT-001 的 ID、卡片／契約與解除保護。

## 目標／架構

**一個世界、兩個視角、兩組操作權限**，而不是兩套獨立世界或移植整份 Surface Lab HTML 覆蓋遊戲。生存視角顯示玩家與卡片之書；創造視角按需展開工程建模工作台。場景資料共用、UI 可隱藏但不是刪除。卡片之書仍由既有 game runtime 管理；Surface Lab JSON 僅代表未綁定的候選環境幾何。

```text
Surface Lab 005 (原檔 / 匯出 JSON)
          │ 唯讀、顯式匯入
          ▼
Surface Geometry Adapter (cm, Y-up, identity)
          │
          ▼
Shared Scene Graph <──── 存檔和卡片契約仍由各自既有系統主管理
       ┌──┴──┐
       ▼     ▼
 Survival   Creative
 Camera     Camera + 建模面板(後續)
 +Player
```

## 本階段第一個可驗證切片 (S0)

- 新增獨立網址：`/living-library/experiments/nw-surface-bridge/index.html`，僅候選分支預覽，正式 `main` 不含此頁。
- 支援 Surface Lab v4 JSON 的「普通平面／底板／立面／非折板表面拉伸」，包含具有穩定父面識別、面板路徑 `front`／`end`／`side:N` 的附著面。公分轉公尺，保持 X、Y、Z 和 Y-up。
- 同一批 Three.js 場景 Mesh，在兩個視角之間切換。不複製或卡片化面體；生存視角只是**第三人稱相機與 WASD 平面走動預覽**，尚無跳躍／重力／碰撞。
- 折板、折板上的附著面、資料不完整／拓撲失效的面體：回報 `gaps` 而非亂畫或自動修補。
- 匯入 JSON 僅留在記憶體，不呼叫卡片契約 API、不修改 localStorage、不寫回工程原檔，也不生成帶權限的 runtime 物件。
- `synthetic-demo.json` 僅為人工合成幾何測試資料，**不是使用者原場景存檔**。

## 下一切片（分批審核；目前未實作）

| 次序 | 技能／交付物 | 驗收 Gate |
|---|---|---|
| S1 | 已有 Surface Lab 折板與親子面拓撲資料的完整 Three.js 轉換，語義對照測試 | 原始錨點／父子依附完全一致，錯誤阻斷 |
| S2 | Surface Lab 創造工具載入到遊戲的**隱藏工具層**，共用同一 scene IDs | 創造模式顯示、返回生存模式隱藏；既有卡片/書無變化 |
| S3 | 玩家角色物理：Y 軸、碰撞、平台、樓梯、跳躍 | 不穿模、不墜落，且既有第三人稱相機／搖桿保留 |
| S4 | 探索／創造編輯的安全持久化、回退、卡片與環境邊界 | 避免覆蓋舊存檔、ID 碰撞與卡片契約重掃 |
| S5 | 區塊世界生成 seed/chunk、手機效能及存檔驗收 | 同 seed 確定性、增量載入、畫面效能 Gate |

每個 S 階段需驗證與審核；未經確認不得將任何段落標示 `approved`。

## 保留／新增／禁止

| 類別 | 内容 |
|---|---|
| 保留 | `living-library/live/**` 正式檔、`main`、卡片之書、真實之眼、全部卡片／契約 ID、玩家照片、圖書館與法陣規則 |
| 新增 | 唯讀 JSON 轉換 adapter、候選 Three.js 獨立展示頁、合成 fixture、單元測試 |
| 禁止 | 複製 Surface Lab 原始存檔到 GitHub、直接改原 HTML、替換世界核心、改任何卡片契約關聯、發布 Pages 正式網址／APK、改別的 repo |
| 已知缺口 | 手機搖桿、真正物理碰撞、跳躍、建模面板整合、折板幾何、活的地形生成、使用者來源場景實體驗證 |

## 驗證及回退

- 靜態接口：輸入 `version=4, unit=cm`；輸出 `nw-surface-scene/v0.1`，`objects`、`gaps`、`readonly`、`collisionStatus`。
- Tests: `node --test tests/nw-surface-bridge.test.mjs`。
- Tests status: V8 adapter smoke **PASS**（合成底板換算）；整套 Node suite、實際瀏覽器、原型頁手機、GitHub Actions、完整真實模型 **NOT_TESTED**，不可宣稱通過。
- Rollback: 關閉 Draft PR 或移除本分支所有新檔即可回退；正式 `main` 不受本 PR 影響。基底參考 SHA 如上。
- Closeout: 確認 PR 通過審核、實驗整合驗收、無現役分支依賴，再遵循新短期分支生命週期規則結案；禁止動舊分支。
