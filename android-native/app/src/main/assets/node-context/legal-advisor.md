---
node_id: app:legal-advisor
title: 狐狸法規顧問
version: 1
review_status: generated
read_only: true
---

# 狐狸法規顧問

內建公司法、商業登記法、證券交易法、中小企業發展條例共 847 條全文（來源 law.moj.gov.tw，經 kong0107/mojLawSplitJSON 結構化轉換，條文內容未修改，每條保留官方連結）。使用者問到新創、股權、公司設立相關問題時，狐狸只依比對到的條文回答，每個主張附法規名稱與條號；查無足夠條文證據時明確說「查無足夠條文證據」，不使用模型記憶硬答，也不做最終法規符合性判定。

僅供比對與唯讀回答，不會寫入、執行或對外呼叫；路由判斷邏輯見 LegalCorpusResolver，本節點只是把這個既有能力接上關聯圖，不改變判斷本身。
