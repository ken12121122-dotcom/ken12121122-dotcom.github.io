# Amin GBA Backup v2 Contract

副檔名：`.agbbackup`

容器：ZIP

格式識別：

```json
{
  "format": "amin-gba-backup",
  "formatVersion": 2
}
```

## 目的

`.agbbackup` 是 Amin Pocket GBA 的可攜式資料搬家格式，用於：

- App 套件更換
- 手機更換
- 清除資料前備份
- Preview 轉正式版
- 故障還原

它不是 ROM 發布格式，也不應上傳或分享包含無授權 ROM 的完整備份。

## ZIP 結構

```text
AminGBA_*.agbbackup
├── manifest.json
├── local-storage.json
└── binary/
    ├── 00000001.blob
    ├── 00000002.arraybuffer
    └── ...
```

## manifest.json

必要欄位：

- `format`
- `formatVersion`
- `createdAt`
- `origin`
- `includeRoms`
- `databaseCount`
- `databaseNames`
- `binaryCount`
- `databases`

每個資料庫快照包含：

- 名稱與版本
- object store schema
- key path
- auto increment 設定
- index 定義
- key/value records

## 兩種備份

### 快速備份

- 不包含 `amin-pocket-rom-library`
- 包含其他 IndexedDB，例如 EmulatorJS／mGBA 存檔資料庫
- 包含 Amin 與 Emulator 相關 localStorage
- 保留 ROM 中繼資料供使用者辨識缺少哪些遊戲

### 完整備份

- 包含 ROM library 與 Blob
- 檔案可能非常大
- 僅供使用者自己的合法備份與裝置搬家

## 支援的資料型別

- primitive values
- `undefined`
- `BigInt`
- `Date`
- `Blob`
- `File`
- `ArrayBuffer`
- TypedArray
- Array
- Object
- Map
- Set

二進位內容存放在 `binary/`，JSON 內只保存型別與路徑。

## 還原規則

1. 匯入前要求使用者確認。
2. 只處理備份中列出的資料庫。
3. 同名資料庫先刪除，再依快照 schema 重建。
4. 不在備份中的資料庫不受影響。
5. localStorage 只還原允許的 Amin／Emulator 前綴。
6. 完成後寫入 `amin-gba-last-restore` 並重新載入頁面。

## 安全邊界

- 不備份任意網站 localStorage。
- 不把 ROM 或存檔送到 GitHub、Supabase 或外部服務。
- 匯入前不執行備份檔中的程式碼。
- 未知格式版本必須拒絕。
- 缺少二進位檔案時必須停止還原。
- 資料庫被其他頁面占用時必須停止，不能假裝成功。

## 相容性

v2 匯入器只接受 v2。未來若新增 v3，應新增明確遷移器，不可默默猜測舊格式。
