# Raw sources

原始來源清單。這些檔案的正本都留在原本的位置，這裡只做索引，不重複內容。權威性以原檔為準；如果這份索引跟原檔對不上，以原檔為準並回報矛盾。

| 來源 | 路徑 | 最後標註更新日期 | 一行摘要 |
|---|---|---|---|
| AI 交接主檔 | [`/AGENTS.md`](../../AGENTS.md) | 2026-07-19 | 發布閘門、完成定義、手把輸入路徑、關鍵檔案清單；任何 AI 改程式前必讀。 |
| 架構文件（人讀） | [`/amin-vault/ARCHITECTURE.md`](../../amin-vault/ARCHITECTURE.md) | 2026-07-19 | Vault 分層、Runtime/原生拆分、手把輸入架構、全域控制盤、GBA 模擬中心、已知限制與下一步。 |
| 架構文件（機讀） | [`/amin-vault/architecture.json`](../../amin-vault/architecture.json) | 對應 ARCHITECTURE.md | JSON 版架構描述，供 CI/工具讀取。 |
| v0.9.0 交接檔 | [`/AMIN_POCKET_GBA_HANDOFF.md`](../../AMIN_POCKET_GBA_HANDOFF.md) | 2026-07-12 | 手把訊號診斷起點、PWA→原生橋接的轉折、v0.9.0 當時的已驗證/未驗證分界表。**已過時**：現況已到 Bridge 23，本檔案許多「未驗證」項目後續文件顯示已完成，見 [`pages/known-issues.md`](../pages/known-issues.md)。 |
| 原生外殼 v0.9.1 預覽說明 | [`/README-NATIVE-SHELL-v0.9.1.md`](../../README-NATIVE-SHELL-v0.9.1.md) | 無標註日期，內容對應 v0.9.1 preview | 網路狀態橋接、cartridge streaming、離線復原頁；強調 preview 簽章非正式管道。 |
| 專案入口 README | [`/README.md`](../../README.md) | 對應 Bridge 16 | 專案一句話介紹、目前驗證版本號、關鍵文件連結。 |
| APK 發布 manifest | [`/amin-vault/native-release-manifest.json`](../../amin-vault/native-release-manifest.json) | `publishedAt: 2026-07-22` | 機器可讀的 APK 正式版本來源：`latestVersionName`、簽章 SHA-256、release notes。 |
| Runtime 發布 manifest | [`/amin-vault/runtime-manifest.json`](../../amin-vault/runtime-manifest.json) | `publishedAt: 2026-07-19` | 機器可讀的 Runtime 版本、entry point、資產清單、capability 清單。 |
| Git 提交歷史 | `git log` | 持續更新 | Bridge 2 → Bridge 23 的實際發布提交序列，是版本演進最原始的紀錄。 |
| （外部）Dify repo | [`github.com/langgenius/dify`](https://github.com/langgenius/dify) | 讀取當下的 default branch | LLM 應用開發平台。收錄目的是研究它的節點-連結（node-link）workflow 架構，見 [`pages/reference-dify-node-architecture.md`](../pages/reference-dify-node-architecture.md)。跟 Amin 專案目前**沒有**已確認的關聯，純參考資料。 |
| 正式簽章契約 | [`/android-native/RELEASE_SIGNING.md`](../../android-native/RELEASE_SIGNING.md) | 隨初始提交 `1a9c84d`，2026-07-19，其後未再更新 | 正式 APK 只能用一把永久 keystore、四個 GitHub Secrets 名稱、release manifest 啟用前必須全欄位正確、發布前要做過一次金鑰復原演練。 |
| 永久簽章身分紀錄 | [`/android-native/PERMANENT_SIGNING_IDENTITY.md`](../../android-native/PERMANENT_SIGNING_IDENTITY.md) | 同上 | 只記錄公開憑證指紋，聲稱 Bridge 1（code 97）起的簽章身分。**⚠️ 其記錄的指紋跟其他所有來源不一致**，見 [`pages/release-process.md`](../pages/release-process.md) 的簽章矛盾段落。 |
| Update Bridge 簽章操作說明 | [`/android-native/UPDATE_BRIDGE_SIGNING.md`](../../android-native/UPDATE_BRIDGE_SIGNING.md) | 同上 | 建立永久 keystore、GitHub Secrets、驗收流程的操作步驟；驗收序列提到 `0.9.2-bridge1`／`rc6`，是 Bridge 1 當時的基準點。 |
| v0.9.2 RC 實機驗收清單 | [`/android-native/RC092_DEVICE_ACCEPTANCE.md`](../../android-native/RC092_DEVICE_ACCEPTANCE.md) | 同上，內容對應 rc2/bridge1 時期 | 逐項勾選的實機驗收表（安裝並存、Runtime 更新、手把回歸、卡匣、備份還原、診斷、離線救援、Update Center、簽章復原、權限中心）；多數 `[ ]` 未勾選，明文寫「全部通過前 release manifest 保持 `enabled: false`」。 |
| Android 原生殼 README（v0.9.0 時期） | [`/android-native/README.md`](../../android-native/README.md) | 同上，內容對應 app version 0.9.0 | 說明原生殼存在原因（Web Gamepad API 收不到面板按鍵）、當時的實作清單與待驗證項目。**已過時**：內容早於 Bridge 16/23，實體控制器驗證等項目後續文件已標記完成。 |
| （對話口述）使用者個人背景 | 無檔案，使用者對話口述 | 2026-08-06 | 使用者自我介紹（嘉義人、志願役空軍 5 年含消防兵與氣象室、之後歷任建築師助理／生產線儲備幹部／倉庫管理員／現職職安主管）。**跟 Amin Pocket GBA 專案本身無關**，使用者要求仍收錄進本 wiki，見 [`pages/user-profile.md`](../pages/user-profile.md)（獨立成頁，不連進任何專案頁面）。 |

## 已知：來源之間的日期落差

`AGENTS.md` 與 `ARCHITECTURE.md` 都標註「最後人工驗證：2026-07-19」且明確寫「正式版本是 Bridge 16」，但 `native-release-manifest.json` 的 `publishedAt` 是 `2026-07-22`、`latestVersionName` 已經是 `bridge23`，git log 也顯示 Bridge 17 到 Bridge 23 都已經有提交。這代表 `AGENTS.md`／`ARCHITECTURE.md` 沒有跟上最新幾次發布 —— 細節與影響見 [`pages/release-process.md`](../pages/release-process.md) 的「⚠️ 矛盾」段落。
