# 念動世界｜歷史博物館（ARCHIVE_ONLY）

> **已脫節的歷史版本與舊分支索引，不是現行遊戲原始碼或設計真相。**
>
> OWNER 2026-10-21：「有的脫節的特別成立一個博物館，跟現在做區別；現行做法如果要分析比較不要去看那邊的。」

## 核心規則

1. 分析「目前如何做、故障、合規、要怎麼修」時，**只以查詢當下最新 `main` 的真實源碼及 `living-library/AGENTS.md` 的 P0（`NW-CORE-CONTRACT-001`）為程式／規則基準**，以及當次 OWNER 核准的候選與實際測試證據。**不得從博物館、舊分支或過時進度摘錄代替目前程式／測試證據。**
2. 只有 OWNER 明確要求「歷史研究、追溯舊版、復原老成果」時才讀取本館，讀取**不**代表可合併或啟用。
3. 博物館中保存的是分支名稱、固定歷史 SHA、差異摘要與開發經驗，不更改任何玩家卡、Git branch、主線代碼或部署。**沒有授權刪除、移動、合併任何舊分支，也沒有啟用 GitHub Rulesets。**
4. 任何變更必須提出確切檔案、函數、影響範圍及回退方式，取得 OWNER 明確核准；不能因「博物館存在」而自動把舊程式整批匯入。
5. 新 P0 禁止：已締約後再次掃描判斷權屬、只靠最近 3D 距離指定解除契約、以物件標題／座標／可見性取代持久身分。舊作法只保留為歷史證據。

## 博物館：已在最新主線之前的歷史參考

2026-10-21 唯讀 Git 比對中，這 8 條分支相對 `main` 為 `ahead_by=0`／`behind>0`，不需重複合併其獨有提交：

- `feat/atlas-player-tuning-20261015`
- `feat/living-atlas-integration-20261015`
- `feat/model-review-card-sandbox-20261015`
- `feature/atlas-progress-review-20261016`
- `feature/living-library-b-workbench-20261013`
- `fix/living-library-live-proof-20261014`
- `living-library-preview-20261013`
- `prototype/living-library-threejs-scene-20261013`

### 舊大型遊戲分支的固定歷史標本

- 舊版 `work/nw-gamedev-p0-20261014` 的 **2026-10-15 原 HEAD**：`5a448b4f99100481bc676767df36a02b2a2a20e8`。
- 對比時 `main`：`e0364126ceb20b405d15c02a79159a91e11c2d49`。
- 當時 `ahead=87`、`behind=182`、差異檔案 87。舊版 `index.html` 約 1747 行、`object-craft.js` 約 119 行，沒有當前 `scan-bundle.js` 與 `rune-unbind-spell.js`，也沒有最高層通用 P0。
- **這個舊 SHA 歸博物館，並不代表整個仍存在的工作分支往後只能做博物館；若分支更新，仍須對比當下最新 main 才能判斷現行成果。**
- 可能有可保留的候選模組：`action-registry.js`、`spell-contract.js`、`spell-executor.js`、`interaction-session.js` 等；是否採用必須再核准。

## 暫不入館的成果

其他與 main 分歧且有獨有提交的分支，不因年代直接宣告作廢；不得自動刪除。涉及 Android／其他專案的超大型分支列「不在本次整合範圍」，而非整條進博物館或併入主線。

## 正在進行的工作指引

讀取 `docs/work-nw-gamedev/CURRENT_BASELINE_20261021.md` 作為同步準備記錄；真正的程式真相仍須即時回讀 `main`。ChatGPT Library 同步使用 `/念動世界_GAME_DESIGN_KB/99_MUSEUM/`（歷史隔離區）。

**這份文件僅新增歷史館入口，不是合併、正式發布或 GitHub 平台鎖定的完成證明。**
