# Capability BOM — Step 11 P0

Baseline: `release/android` plus the P0 conversational capability entry.

## Summary

| Measure | Count | Meaning |
| --- | ---: | --- |
| Existing Capability total | 43 | 26 Registry capabilities plus 17 existing commands |
| Chat-addressable | 42 | Fox can identify/read the Node context or the existing catalog can resolve the command |
| Bridge required | 1 | The system root has no managed MD context and is not a user function |
| Roadmap-only / not implemented | 0 | Roadmap ideas are not inserted into the runtime inventory |
| Managed Node MD assets | 25 | Every built-in functional Node; reference Nodes are excluded from the BOM |

`Chat-addressable` is read/report/resolve only. It does not authorize the LLM to
execute a command, mutate the Graph or MD, write GitHub, merge, release, extend
itself, or act autonomously.

## Registry capabilities

| Capability | Program entry | Current state | Chat | Bridge gap |
| --- | --- | --- | --- | --- |
| `app:app-core` — Amin Pocket root | `NodeRegistry` synthetic root; route `amin-home://open` | Implemented system aggregate | Bridge required | Either add a root summary MD or keep it intentionally non-chat |
| `app:prompt-keyboard` — 提示詞鍵盤 | `PromptKeyboardSetupActivity`; `amin-prompts://setup` | Implemented | Direct, read-only MD + existing route | None |
| `app:prompt-manager` — 提示詞管理 | `PromptManagerActivity`; `amin-prompts://manage` | Implemented | Direct, read-only MD + existing route | None |
| `app:appearance` — 外觀設定 | `AppearanceSettingsActivity`; `amin-appearance://settings` | Implemented | Direct, read-only MD + existing route | None |
| `app:control-center` — 控制中心 | `ControlCenterActivity`; `amin-home://open` | Implemented | Direct, read-only MD + existing route | None |
| `app:graph` — 關聯圖 | `WikiGraphActivity`; `amin-graph://open` | Implemented | Direct, read-only MD + existing route | None |
| `app:control-api` — Control API | `AminControlApiActivity`; `amin-api://settings` | Implemented | Direct, read-only MD + existing route | None |
| `app:brain-control` — AMIN Brain | `BrainControlActivity`; `amin-brain://open` | Implemented, private-repo backed | Direct, read-only MD + existing route | Requires OWNER GitHub App account setup before it connects |
| `app:voice` — 語音入口 | `VoiceCommandActivity`; `amin-voice://open` | Implemented | Direct, read-only MD + existing route | None |
| `app:voice-catalog` — 語音指令 | `VoiceCommandCatalogActivity`; `amin-voice://commands` | Implemented | Direct, read-only MD + existing route | None |
| `app:permissions` — 權限中心 | `PermissionCenterActivity`; `amin-permissions://open` | Implemented | Direct, read-only MD + existing route | None |
| `app:update` — 版本與更新 | `UpdateHubActivity`; `amin-update://check` | Implemented | Direct, read-only MD + existing route | None |
| `app:fox-chat` — 狐狸 | `VoiceOrbHomeActivity`; `amin-fox://chat` | Implemented shared LLM chat entry | Direct, reads `fox-chat.md` | Desktop presentation is connected through the read-only Presentation bridge |
| `app:fox-pet-control` — 狐狸控制面板 | `FoxPetControlActivity`; `amin-fox://control` | Implemented | Direct, read-only MD + existing route | Overlay permission remains explicit Android user approval |
| `app:fox-desktop-pet` — 桌面狐狸 | `FoxPetOverlayService` through `FoxPresentationBridge` | Implemented Presentation capability | Direct, read-only MD; Runtime sends state/reply | No second Router or Voice Runtime; device Overlay needs phone acceptance |
| `finance` — 財務 | `FinanceActivity`; `amin-finance://home` | Implemented | Direct, read-only MD + existing route | None |
| `finance.transaction.create` — 新增收支 | `FinanceTransactionActivity`; `amin-finance://new-transaction` | Implemented | Direct description/route only | LLM execution intentionally disabled |
| `finance.expense.food.view` — 餐飲 | `DataViewActivity`; `amin-data://food` | Implemented | Direct description/route only | Live values still require existing storage path; Fox must not invent them |
| `finance.transactions.view` — 收支明細 | `NodeRegistry` virtual page; `amin-data://transactions` | Implemented | Direct, read-only MD + existing route | Live values still require existing storage path |
| `finance.categories.view` — 分類 | `NodeRegistry` virtual page; `amin-finance://categories` | Implemented | Direct, read-only MD + existing route | Live values still require existing storage path |
| `finance.accounts.view` — 帳戶 | `NodeRegistry` virtual page; `amin-finance://accounts` | Implemented | Direct, read-only MD + existing route | Live values still require existing storage path |
| `finance.assets.view` — 資產 | `NodeRegistry` virtual page; `amin-finance://assets` | Implemented | Direct, read-only MD + existing route | Live values still require existing storage path |
| `finance.storage.transactions` — Transactions Sheet | `NodeRegistry` virtual storage page; `GoogleSheetsAdapter` | Implemented storage record | Direct description through managed MD | Fox receives no live sheet data in P0 |
| `finance.storage.categories` — Categories Sheet | `NodeRegistry` virtual storage page; `GoogleSheetsAdapter` | Implemented storage record | Direct description through managed MD | Fox receives no live sheet data in P0 |
| `finance.storage.accounts` — Accounts Sheet | `NodeRegistry` virtual storage page; `GoogleSheetsAdapter` | Implemented storage record | Direct description through managed MD | Fox receives no live sheet data in P0 |
| `finance.storage.assets` — Assets Sheet | `NodeRegistry` virtual storage page; `GoogleSheetsAdapter` | Implemented storage record | Direct description through managed MD | Fox receives no live sheet data in P0 |

## Existing command capabilities

These 17 records are commands, not Nodes. They reuse the structured
`VoiceCommandCatalog` title, description, phrases, and action as their chat
description; they do not need a duplicate per-command MD. P0 may report or
resolve them but does not dispatch them from an LLM response.

| Capability | Program entry | Current state | Chat | Bridge gap |
| --- | --- | --- | --- | --- |
| `command:overlay_open` — 開啟鍵盤控制 | `VoiceCommandCatalog` → `OVERLAY_OPEN` | Implemented | Direct catalog lookup | Execution remains on existing deterministic command path |
| `command:overlay_close` — 關閉鍵盤控制 | `VoiceCommandCatalog` → `OVERLAY_CLOSE` | Implemented | Direct catalog lookup | Same |
| `command:voice_bubble_open` — 開啟語音浮動按鈕 | `VoiceCommandCatalog` → `VOICE_BUBBLE_OPEN` | Implemented | Direct catalog lookup | Same |
| `command:voice_bubble_close` — 關閉語音浮動按鈕 | `VoiceCommandCatalog` → `VOICE_BUBBLE_CLOSE` | Implemented | Direct catalog lookup | Same |
| `command:mode_cursor` — 游標模式 | `VoiceCommandCatalog` → `CONTROL_MODE_SET` | Implemented | Direct catalog lookup | Same |
| `command:mode_scroll` — 捲動模式 | `VoiceCommandCatalog` → `CONTROL_MODE_SET` | Implemented | Direct catalog lookup | Same |
| `command:system_back` — 返回上一頁 | `VoiceCommandCatalog` → `SYSTEM_BACK` | Implemented | Direct catalog lookup | Same |
| `command:system_home` — 回到首頁 | `VoiceCommandCatalog` → `SYSTEM_HOME` | Implemented | Direct catalog lookup | Same |
| `command:cursor_tap` — 點擊游標位置 | `VoiceCommandCatalog` → `CURSOR_TAP` | Implemented | Direct catalog lookup | Same |
| `command:cursor_long_press` — 長按游標位置 | `VoiceCommandCatalog` → `CURSOR_LONG_PRESS` | Implemented | Direct catalog lookup | Same |
| `command:direction_up` — 向上 | `VoiceCommandCatalog` → `DIRECTION_UP` | Implemented | Direct catalog lookup | Same |
| `command:direction_down` — 向下 | `VoiceCommandCatalog` → `DIRECTION_DOWN` | Implemented | Direct catalog lookup | Same |
| `command:direction_left` — 向左 | `VoiceCommandCatalog` → `DIRECTION_LEFT` | Implemented | Direct catalog lookup | Same |
| `command:direction_right` — 向右 | `VoiceCommandCatalog` → `DIRECTION_RIGHT` | Implemented | Direct catalog lookup | Same |
| `command:open_gba` — 開啟遊戲庫 | `VoiceCommandCatalog` → `OPEN_GBA` | Implemented | Direct catalog lookup | Same |
| `command:open_controller_settings` — 開啟控制器設定 | `VoiceCommandCatalog` → `OPEN_CONTROLLER_SETTINGS` | Implemented | Direct catalog lookup | Same |
| `command:voice_stop` — 停止語音 | `VoiceCommandCatalog` → `VOICE_STOP` | Implemented | Direct catalog lookup | Same |

## Reuse and gap decision

The implemented slice follows the required order:

1. Existing Capability: reuse `NodeRegistry`, `VoiceCommandCatalog`, existing
   routes, activities, and command descriptions.
2. Repo: reuse `CapabilityInventoryStore`, `SharedGraphSyncKernel`,
   `UnifiedGraphProvider`, `GraphContract`, and Node approval/storage paths.
3. GitHub reusable implementation: no search is needed for this local read-only
   bridge because the repository already contains all required primitives.
4. Small internal implementation: add only the bounded MD resolver, relevance
   selector, and read-only conversation contracts.
5. API/MCP: not used by P0.
