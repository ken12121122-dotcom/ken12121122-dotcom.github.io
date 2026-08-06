# Reference: Dify 的節點-連結（node-link）工作流架構

來源：[`github.com/langgenius/dify`](https://github.com/langgenius/dify)（外部 repo，shallow clone 讀取，非 Amin 專案本身的程式碼）——實際讀取的檔案：`api/core/workflow/node_factory.py`、`api/core/workflow/workflow_entry.py`、`api/core/trigger/constants.py`、`web/app/components/workflow/types.ts`、`web/app/components/workflow/nodes/`、`web/app/components/workflow/custom-edge.tsx`。

## 這是什麼專案

Dify 是一個 LLM 應用開發平台，其中一個核心功能是「Workflow」：使用者在畫布上拖拉節點（node）、用連結（edge/link）把節點串起來，定義一個可執行的 LLM 應用流程（例如：輸入 → LLM 判斷分類 → 依分類走不同節點 → 呼叫工具 → 輸出）。這篇整理的是這個節點-連結圖的底層架構，不是 Dify 整體功能介紹。

## 兩層圖的概念：視覺執行圖 vs 資料流圖

這是最值得記住的一點：Dify 的 workflow 實際上同時存在**兩種「連結」**，容易被畫面上看到的線誤導成只有一種：

1. **視覺／執行圖**（前端畫的那些線）：`web/app/components/workflow/types.ts` 定義 `Node = ReactFlowNode<CommonNodeType>`、`Edge = ReactFlowEdge<CommonEdgeType>`——整個編輯器是用 [React Flow](https://reactflow.dev/) 蓋的。這些 edge 決定的是**執行順序／分支**（例如 if-else 節點的兩個輸出各接不同下一步）。
2. **資料流圖**（節點怎麼拿到別的節點的輸出）：後端有一個 `VariablePool`，節點輸出用 `ValueSelector = [nodeId, key]` 這種路徑定址。某個節點要用到上游節點的資料，不是靠畫面上的線直接傳值，而是在自己的設定裡寫一個 selector 指到 `[上游nodeId, 輸出欄位]`，執行時從共用的 VariablePool 查出來。

也就是說：畫面上兩個節點之間拉一條線 ≠ 資料一定從那條線流過去；線代表「先執行誰、下一步是誰」，資料引用是另一套獨立的定址機制。這對任何要做「畫布式節點串接」的系統都是重要的設計取捨參考。

## Node 的兩層來源：graphon（底層圖引擎，外部套件）＋ core.workflow.nodes（Dify 專屬節點）

讀 `api/core/workflow/node_factory.py` 才發現一件事：Dify 已經把底層的圖執行引擎抽成一個獨立套件 **`graphon`**（`pyproject.toml` 鎖 `graphon==0.7.0`，是 PyPI 相依套件，不在這個 repo 裡)。分工是：

- **`graphon`**：通用圖引擎本體 —— `Graph`、`GraphEngine`、`GraphRuntimeState`、`VariablePool`、`Node` 基底類別、`NodeType`／`BuiltinNodeTypes` enum，以及一批通用節點（`start`、`end`、`if-else`、`code`、`http request`、`llm`、`template-transform`、`question-classifier`、`parameter-extractor`、`tool`、`agent`、`document-extractor`、`human-input`、`iteration`、`loop`、`list-operator`、`variable-assigner`、`answer`／`assigner`）。
- **`core.workflow.nodes`**（這個 repo 裡）：Dify 自己疊上去的節點 —— `agent_v2`（新版 Agent，串接獨立的 agent backend）、`datasource`、`knowledge_index`、`knowledge_retrieval`、`trigger_plugin`／`trigger_schedule`／`trigger_webhook`（觸發器類節點，前端對應 `trigger-*`）、`human_input`（審核／人工介入節點的 Dify 專屬 adapter）。

`register_nodes()`（`node_factory.py:118`）用 `pkgutil.walk_packages` 把 `graphon.nodes` 跟 `core.workflow.nodes` 兩包一起 import，讓每個節點類別「自我註冊」進一個全域登記表（`Node.get_node_type_classes_mapping()`），再包成 `NODE_TYPE_CLASSES_MAPPING: Mapping[NodeType, Mapping[version, type[Node]]]`——同一個 `NodeType` 可以有多個版本的實作並存，預設抓 `"latest"`，也可以指定舊版本（相容性/漸進升級用）。

## 節點怎麼被造出來：NodeFactory + 依賴注入

`DifyNodeFactory.create_node()`（`node_factory.py:388`）是核心：輸入一份節點的 JSON 設定（`{id, data: {type, version, ...}}`），流程是：

```text
node_config (dict)
→ adapt_node_config_for_graph()      # 相容性轉換
→ NodeConfigDictAdapter 驗證          # pydantic 驗證外形
→ resolve_workflow_node_class()       # 依 (node_type, node_version) 找到對應類別
→ 用該類別自己的 schema 再驗證一次資料
→ 依 node_type 查一個 factories 字典，組出這個節點專屬需要的依賴
   （例如 CODE 節點要 code_executor/code_limits；
     HTTP_REQUEST 節點要走 SSRF proxy 的 http_client；
     LLM 節點要 model_instance、memory、file saver；
     AGENT 節點要 agent backend client、runtime request builder）
→ node_class(node_id=..., data=..., **node_init_kwargs) 建構出節點物件
```

值得學的地方：**節點的執行期依賴（HTTP client、程式碼沙箱、模型憑證…）是在「造節點」這一步用工廠字典注入進去，不是節點自己在執行時去 import 全域單例**。想加一種新節點類型，只要在 `node_init_kwargs_factories` 加一個 case，不用動到既有節點的程式碼。

## 執行：GraphEngine + 事件串流 + 可插拔 Layer

`workflow_entry.py` 顯示執行是用 `graphon.graph_engine.GraphEngine`，`engine.run()` 產生一連串 `GraphEngineEvent`（每個節點開始/完成/失敗等事件），外層再包一層 `filter_graph_events()` 做串流相容性處理。橫切關注點不是寫死在每個節點裡，而是用「Layer」疊上去，例如：

- `ObservabilityLayer`（可觀測性）
- `LLMQuotaLayer`（LLM 用量額度控管）
- `DebugLoggingLayer`
- `ExecutionLimitsLayer`（執行限制，例如逾時/步數上限）

還有 `CommandChannel`（`InMemoryChannel`）與 `ContainerAwaitRequest`，用來支援「跑到一半暫停、之後再恢復」——這是 `human-input` 節點（需要真人審核/輸入才能繼續）之類場景需要的能力。

## 前端：畫布是 React Flow，每種節點一個資料夾

`web/app/components/workflow/nodes/` 底下每種節點類型一個資料夾（`llm`、`code`、`if-else`、`http`、`tool`、`agent`、`agent-v2`、`knowledge-base`、`knowledge-retrieval`、`document-extractor`、`human-input`、`iteration`／`iteration-start`、`loop`／`loop-start`／`loop-end`、`variable-assigner`、`list-operator`、`trigger-webhook`／`trigger-schedule`／`trigger-plugin`、`data-source`、`start`／`end`、`answer`、`assigner`、`parameter-extractor`、`question-classifier`、`template-transform`），每個資料夾放該節點在畫布上的面板 UI 與預設設定（`NodeDefault<T>`：分類、排序、標題、是否為起點、是否可刪除等 metadata）。整張圖（nodes + edges + viewport）可以匯出成 DSL（YAML）分享或版本控管，對應 `update-dsl-modal.tsx` / `dsl-export-confirm-modal.tsx`。

## 這篇筆記跟 Amin 專案的關係

目前**沒有**已確認的關係——這是使用者主動要求收錄的獨立參考資料，不是 Amin Pocket GBA 專案的 raw source。如果之後要拿這個架構套在 Amin Control API v1（Bridge 23 新增的語音/REST/WebSocket/自動化統一入口）上做類似「節點化」的動作編排，可以回來這篇當參考，但目前 `amin-wiki` 其他頁面（尤其 [`pages/known-issues.md`](./known-issues.md) 待確認的 Action Core 共用問題）不應該假設兩者已經有關聯。
