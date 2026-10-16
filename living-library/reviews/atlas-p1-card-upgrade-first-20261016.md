# 萬物圖鑑 P1｜卡牌升級先行 · 開發交接 v0.1

- project_id: `NW-ATLAS`
- issue_id: `ATLAS-006`
- card_id: `public.gaming.atlas`
- review_status: `generated`
- implementation_state: `prototype_only`
- requires_owner_approval: `true`
- release_status: `candidate_not_published`

## 使用者最新調整
先讓原有普通卡片完成升級資格與申請流程，再處理唯一性和永久編號。停止以 `ATLAS-0001` 作為預設的圖鑑本體 ID，也不得讓四位英數格式成為正式決定。

原先規則頁的「卡槽收藏序號」以 `001、002、...、999` 起始顯示；超過 999 才依數字自然延伸為 1000，沒有設定上限。這與「圖鑑本體 ID」屬於不同的概念，不能混淆。

## 升級候選流程（不執行實際升級）

```text
已存在的普通卡（保留 local.card.* 身分）
        ↓
提出升級申請（目的、卡片身分、版本）
        ↓
AI 審查（待實作）+ 使用者本人核准（待實作）
        ↓
世界物件原始錨點綁定與唯一性檢查（待實作）
        ↓
L2 唯一實體正式認證與卡面唯讀（待實作）
        ↓
核發永久收藏位置／卡槽編號（最後才執行）
```

## 本 PR 做了什麼
- `atlas-card-upgrade-plan.js`：只驗證升級申請草稿完整性、不更動原卡、不自動核准、不發號。
- `atlas-rulebook-review.html`：刪去 `ATLAS-0001` 及候選 ID 格式檢查，新增升級申請預覽；界面明確顯示未實作與未配號。
- `atlas-development-progress.json`：ATLAS-006 變更為 P1 開發中；ATLAS-002 延後至 P2 受阻，留下追溯紀錄。
- Node 和瀏覽器回歸確認：原始 card ID 不變，申請草稿即使完整也不可認證 L2 或配號。

## 驗收與禁止事項
- 原有 ObjectCraft、3D 場景、GBA 不得改動。
- 審查表單使用者手填資料僅供試算，不讀取其真實遊戲卡片。
- 不得建立永久 ID、不核發正式卡槽、不自動讓普通卡轉成 L2。
- 真正跨裝置唯一身份與世界錨點、不可變授權交易，待 OWNER 核准後另案實作。
