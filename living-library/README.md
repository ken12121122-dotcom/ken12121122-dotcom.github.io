# Living Library · 獨立 3D 念動圖書館試作

狀態：**Prototype / generated / 未合併主網站**

## 目的
以 Three.js 建立一個可在手機操作的獨立 3D 概念場景，供 OWNER 討論：以人為中心的魔法圖書館、環繞書本、書庫、召喚、歸架、閱讀書頁。不要整合到 PackageCanvas 或既有 GEN2 系統。

## 實作範圍
- 僅新增 `living-library/index.html` 與本說明；不更動現有網站、APK、GEN2 或授權。
- THREE.js 0.185.0（與 repo 現有 3D 場景版本一致；使用 jsDelivr CDN）。
- 3D 大廳：書櫃、柱廊、石台、法陣、光源、漂浮粒子、匿名主角與環繞書本。
- 10 本合成展示書籍：6 本主動環繞、4 本已歸檔。
- 互動：拖曳轉動視角、滑鼠滾輪/手機雙指縮放、點擊書本、前後選取、展開書頁、召喚與歸架、暫停動畫、重設視角。
- 手機窄螢幕書庫抽屜與書頁彈窗；WebGL/Three.js 載入失敗時顯示 fallback。
- 3D scene and UI data are synthetic; no Google/GitHub login, calendar, cloud access or private knowledge.

## 保留但不實作的正式系統連結

| Demo 書籍 | 可能連結到的正式功能（候選，尚未啟用） |
|---|---|
| 研究之書 | 研究導航、文獻流程與研究問題 |
| 創業之書 | PackageCanvas 創業任務與公司設立 |
| 知識世界 | Knowledge Map、GBA 知識世界與 Knowledge Graph |
| 任務之書 | 每日任務、日曆與工作流 |
| 學習之書 | 學習科學研究方法與教學驗證 |
| 資產之書 | 財務自由、授權與公司退場 |
| 職安／寫作／設計／教學 | 歷史技能與成果書架 |

## 未實作、待討論
- 尚未建立與既有頁面的跳轉、帳戶認證、持久進度或知識庫串接。
- 書架上的歸檔書是示意，未實作實體書本飛回指定書架的完整動作與分類對位。
- 目前使用第三人稱的匿名主控者剪影；第一人稱視角與真正的語音／手勢／意念控制尚未做。
- 書籍數量／色系／分類與互動規則均待 OWNER 審視。

## 驗收
- [x] 兩段 JS 在語法層可解析（classic script、ES module 主體）。
- [x] 入口只建立在 `living-library/`；沒有動到現存頁面。
- [ ] 實機 WebGL 畫面、旋轉、縮放、點書互動與效能檢查。
- [ ] OWNER 審查視覺與交互感受。
- [ ] OWNER 明確核准後，才能合併正式 `main` 或與既有頁面建立連結。

## 回退
刪除獨立 `living-library/` 資料夾即可撤除所有試作，不會變動現有 Canvas 資料或 Android App。
