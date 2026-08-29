# Amin Android 手機更新通道操作手冊

本文件是 Amin Pocket GBA 原生 APK 候選版、手機驗收與正式發布的單一操作手冊。Codex、Claude Code、GitHub Actions 或其他 Agent 不得自行改寫發布語義；開始操作前必須先完整閱讀根目錄 `AGENTS.md`，再閱讀本文件。

## 1. 適用範圍與權威來源

本流程只處理 `com.amin.pocketgba` 的 Android 原生 APK 更新，不處理 `amin-vault/runtime-manifest.json` 的 Web Runtime 熱更新。

權威檔案與端點：

- 發布閘門：`AGENTS.md`
- 版本資料：`android-native/release-version.json`
- 建置與發布：`.github/workflows/android-release.yml`
- 永久簽章規則：`android-native/RELEASE_SIGNING.md`、`android-native/UPDATE_BRIDGE_SIGNING.md`
- 對外更新清單：`main` 分支的 `amin-vault/native-release-manifest.json`
- 手機實際讀取端點：`https://ken12121122-dotcom.github.io/amin-vault/native-release-manifest.json`
- APK 公開位置：`main` 分支的 `amin-vault/releases/`

永久套件與簽章身分不得改變：

- package：`com.amin.pocketgba`
- signer certificate SHA-256：`3b9a3125b2cd19389c284e834c4ff9eb67caeecb647fe41897d923169f4152c7`

Keystore、密碼及 Base64 內容只能存在 GitHub Actions Secrets 或受保護的離線備份，禁止寫入 Git、Issue、PR、log、聊天或 handoff 文件。

## 2. 不可跨越的發布閘門

1. 功能開發只能在功能分支與 PR 進行，不得直接推送 `main`。
2. 一般 PR CI 的 Debug APK 不是更新通道 APK；它的簽章不同，不能覆蓋手機上的正式 App。
3. 候選版必須由 `Android Candidate / Production Release` 工作流使用永久簽章建置。
4. 發布候選版到手機更新通道前，必須取得 OWNER 對該候選分支或版本的明確要求。
5. 候選版發布不等於 PR 合併、不等於正式驗收，也不得把功能標示為完成。
6. 正式版發布前必須取得手機實機驗收結果；失敗、跳過或未執行的必要測試都必須如實記錄。
7. 不得以舊版清單覆蓋較高的 `latestVersionCode`。Android 更新不能降版；需要回退時，必須用相同永久簽章建置一個更高 `versionCode` 的修復版。

## 3. 候選版版本資料

候選分支的 `android-native/release-version.json` 至少必須符合：

- `channel` 為 `candidate`
- `versionName` 明確含 `-rcN`
- `versionCode` 大於目前公開更新清單
- `bridge` 與 `releaseSequence` 不重用既有發布值
- `packageId` 與永久 signer fingerprint 不變
- `releaseNotes` 只描述此候選版實際包含、可以驗收的行為

Suggested Task、文件更新或 CI 成功都不會自動構成發布批准。Agent 必須保留候選分支的 immutable commit SHA，讓 APK、Scanner Evidence 與驗收版本能對應同一 revision。

## 4. 候選版發佈到手機更新通道

### 4.1 發布前檢查

1. PR 指向 `release/android`，且 Android PR Validation 成功。
2. PR 尚未合併；`release-version.json` 仍為 candidate。
3. `versionCode` 高於目前手機通道版本。
4. OWNER 已明確要求透過 App 內更新通道取得此候選版。
5. 沒有任何必要測試被偽裝成已完成。

### 4.2 啟動永久簽章工作流

使用功能分支作為精確 ref，不要改用 `release/android`：

```powershell
gh workflow run .github/workflows/android-release.yml `
  --repo ken12121122-dotcom/ken12121122-dotcom.github.io `
  --ref <candidate-branch>
```

工作流必須成功完成下列階段：

- 鎖定 immutable source revision
- 驗證 candidate distribution gate
- 產生 revision-matched Scanner Evidence（適用時）
- Java／JUnit 單元測試
- Android lint
- 永久簽章 APK 建置
- package、versionCode、versionName 與 signer fingerprint 驗證
- APK SHA-256 與大小產生
- 將 APK 與 candidate manifest 發佈到 `main`
- 上傳 distribution evidence artifact

任何階段失敗都不得手動拼湊 manifest 或改用 Debug APK。

### 4.3 驗證 GitHub 與手機實際端點

工作流成功後，要分別驗證兩份資料：

1. GitHub `main` 的 `amin-vault/native-release-manifest.json`
2. GitHub Pages 公開 URL（加 cache-busting query）

兩者必須同時符合預期的：

- `channel: candidate`
- `enabled: true`
- `latestVersionCode`
- `latestVersionName`
- `apkUrl`
- `apkSha256`
- `signerCertificateSha256`

GitHub Pages 可能比 `main` 晚數分鐘同步。在公開 URL 尚未更新前，不得通知 OWNER 開始手機更新。

候選驗收期間，避免合併任何會觸發 `release/android` production workflow 的 `android-native/**` 變更，否則正式版 manifest 可能覆蓋 candidate manifest。若外部變更已發生，先重新檢查公開端點，必要時重新執行同一 candidate ref 的工作流。

## 5. OWNER 在手機上的操作

公開端點確認完成後，只需要 OWNER 介入一次：

1. 開啟 Amin App 的白色原生控制中心。
2. 進入「版本更新／Release & Update」。
3. 按「檢查更新」，確認畫面顯示預期 candidate 版本。
4. 下載並安裝；Android 應允許直接覆蓋，且保留 App 資料。
5. 完成該 PR 的手機驗收清單。

如果 Android 顯示簽章衝突或無法安裝，不得先解除安裝既有 App；先回報完整錯誤，以免遺失 GBA 存檔、設定或其他私有資料。

Agent 在這個階段應進入 `WAITING_PHONE_ACCEPTANCE`，不要求 OWNER 對每個工程步驟逐次回覆。通過時可使用明確訊息，例如：

```text
#<PR> 手機驗收通過
```

## 6. 驗收通過後的正式收尾

1. 將功能分支的版本資料由 candidate 改為正式 `bridge` channel。
2. 若 OWNER 已安裝 candidate，正式版 `versionCode` 必須再提高，才能由 Android 原地更新；同時配置新的 `bridge` 與 `releaseSequence`，不得重用 candidate 值。
3. 移除 `-rcN`，release notes 只保留已驗證內容。
4. 重跑 PR Validation 與所有發布閘門。
5. 完成 PR 審查後才合併至 `release/android`。
6. 由 production workflow 使用同一永久簽章建立正式 APK，並更新 `main` 的正式 manifest。
7. 再次驗證 GitHub `main`、GitHub Pages 公開 manifest、APK URL、hash、版本與 signer。
8. 更新 `AGENTS.md`、相關 manifest 與架構文件中的「最後實機驗證」狀態。

正式發布完成後，才可把 PR／功能標示為完成並進入依賴該功能的下一工程階段。

## 7. Agent 回報格式

每次候選或正式發布至少回報：

```text
狀態：CANDIDATE_PUBLISHED | WAITING_PHONE_ACCEPTANCE | ACCEPTED | PRODUCTION_PUBLISHED | FAILED
source_ref：<branch>
source_sha：<immutable commit>
version：<versionName>
version_code：<integer>
channel：candidate | bridge
workflow_run：<GitHub Actions URL>
public_manifest：<URL and observed version>
apk_sha256：<hash>
signer_sha256：<public certificate hash>
checks_passed：<list>
checks_failed_or_skipped：<list>
formal_manifest_changed：yes | no
rollback：<safe higher-version recovery plan or previous source commit>
next_human_gate：<none or exact phone action>
```

禁止只回報「已發布」「完成」或「可更新」而沒有公開端點、版本、簽章與檢查證據。

## 8. 與 Amin Brain 工程順序的關係

手機候選版驗收只負責關閉對應 PR 的實機閘門，不改變 Amin Brain 的 Graph Contract：

- 不得因發布工作另建 Graph engine、identity、dedupe 或 Canvas。
- `#111` 驗收與合併完成前，不得在 `release/android` 另寫 Shared Graph Sync Kernel。
- Shared Kernel 只能抽取已驗證的 stable identity、dedupe、incremental merge、firstSeen、lastSeen 與 stale-on-missing 語義。
- GitHub Work Observer 必須等 Step 9 Core、9A、9B、9C 與 stale ownership contract 完成後才可開始。

