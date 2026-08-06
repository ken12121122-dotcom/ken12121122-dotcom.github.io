# Architecture overview

來源：[`AGENTS.md`](../AGENTS.md)、[`amin-vault/ARCHITECTURE.md`](../amin-vault/ARCHITECTURE.md)、[`amin-vault/architecture.json`](../amin-vault/architecture.json)、[`README.md`](../README.md)

## 一句話

Amin Pocket GBA 不是單純的 GBA 模擬器，而是「Android App＋GitHub Pages 熱更新 Runtime＋GBA 模擬器＋全域無障礙控制盤」組成的個人控制中介層，長期目標是統一手機、電腦、手把、藍牙、Wi-Fi、投影與未來外接裝置的控制路徑。

## 三個產品介面

1. **白色 Android 原生控制中心** — 主要入口，版本管理。
2. **GBA Runtime** — ROM 庫、控制器設定、EmulatorJS + mGBA。
3. **黑色舊 Pocket OS** — 封存/實驗用，不是預設首頁。

從 GBA 返回應導向白色原生控制中心，不是黑色舊介面（`ARCHITECTURE.md`）。

## Runtime 與原生的分工

```text
Android APK（Java／Kotlin，需要重新 build 才能更新）
├─ WebView 外殼、原生橋接
├─ 檔案選擇器、原生 ROM staging
├─ APK 更新中心
├─ KeyEvent / MotionEvent 手把橋接
└─ AccessibilityService 全域控制盤

GitHub Pages Runtime（amin-vault/，JS/HTML/CSS，熱更新不需重裝 APK）
├─ ROM 庫與存檔保護
├─ 控制器設定
├─ EmulatorJS 前端
├─ mGBA WebAssembly 核心
└─ 由 runtime-manifest.json 控管的熱更新資產
```

判斷原則：Java、Manifest、原生 Activity、AccessibilityService 或 APK 內嵌資產的改動 → 需要新 Bridge APK。JS/HTML/CSS/控制器映射修正 → 走 Runtime 熱更新管道即可（`AGENTS.md` 修改規則、`ARCHITECTURE.md`）。

## Vault 四層（資料哲學）

`ARCHITECTURE.md` 定義的核心原則：**資料屬於 Vault，不屬於任何單一 App**。Android APK、GitHub Pages Runtime、GBA 模擬器、全域控制盤、Obsidian、ChatGPT、Codex、PWA、Google Drive 都只是這個 Vault 的客戶端／轉接器／鏡像層。

1. **Data Core** — Supabase：workspaces、objects、relationships、versions、change requests、audit logs、sync runs、mirror-file registry。
2. **Governance** — `Capture → Change Request → Review → Publish → Version Snapshot → Audit Log`，搭配 RLS、owner binding、樂觀版本檢查、衝突阻擋、公開/私有分離。
3. **Platform Adapters** — Android 原生外殼、GBA Runtime、無障礙全域控制盤、Three.js PWA 外殼、ChatGPT/Codex、Google Drive 鏡像、Obsidian、未來 MCP。
4. **Mirror and Recovery** — `Supabase → Google Drive Markdown Mirror → Obsidian`。

這個四層模型跟本 wiki（`amin-wiki/`）是不同層次的東西：Vault 四層講的是這個專案「產品內」要蓋的資料架構，本 wiki 是給協作 AI 用的專案知識庫，兩者不要混為一談。

## 手把輸入架構

見 [`controller-input.md`](./controller-input.md)（整合了 `ARCHITECTURE.md` 目前狀態與 `AMIN_POCKET_GBA_HANDOFF.md` 的歷史診斷過程）。

## 版本狀態

見 [`release-process.md`](./release-process.md)（含 raw 來源之間的版本號矛盾標註）。

## 已知限制與下一步

見 [`known-issues.md`](./known-issues.md)。
