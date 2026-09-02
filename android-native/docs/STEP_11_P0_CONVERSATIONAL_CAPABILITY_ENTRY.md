# Step 11 P0 Conversational Capability Entry v1

Status: P0 implementation contract

## Purpose

Reuse the existing voice/chat surfaces so the OWNER can ask which capabilities
exist, locate a capability, and see what bridge is missing. This slice is a
read-only query path, not a Tool or Agent execution runtime.

## Reused foundations

- `VoiceOrbHomeActivity` and `FloatingVoiceController` remain the chat entries.
- `NodeRegistry` and `VoiceCommandCatalog` remain the capability sources.
- `CapabilityInventoryStore` remains the read-only inventory.
- `SharedGraphSyncKernel` remains the only identity/dedupe/merge engine.
- `UnifiedGraphProvider` and the existing Dynamic Canvas remain the only graph
  read model and visualization surface.
- `RegistryApprovalActivity` and `PermissionCenterActivity` remain the future
  human-approval and permission surfaces; P0 does not invoke either one.

## Runtime flow

```text
existing chat input
  -> ConversationalCapabilityRuntime
  -> ReadOnlyCapabilityContextBuilder
  -> CapabilityResolver
  -> structured read-only answer
```

Capability questions are intercepted before the legacy Node/Command execution
paths. Ordinary conversation and previously existing behavior remain unchanged.

## Context v1

```yaml
format: amin-conversation-capability-context
version: 1
boundary:
  mode: read_only
  execution_allowed: false
  graph_mutation_allowed: false
  github_write_allowed: false
  self_extension_allowed: false
  autonomy_level: none
summary:
  total: integer
  chat_addressable: integer
  bridge_required: integer
  type_counts: object
capabilities: []
source_records: []
unresolved_gaps: []
```

## Resolution policy

The executable order is fixed as:

1. existing capability;
2. repository implementation;
3. reusable GitHub implementation;
4. small internal implementation;
5. API / MCP / Connector.

P0 executes only step 1. If no existing capability matches, the response marks
`repository_implementation` as the next stage; it does not search the network,
generate code, create a branch, or register a new capability.

## Chat addressability

- A `COMMAND` is addressable through the existing command catalog.
- A Registry capability is addressable when it has a registered managed MD
  context, or through its existing `voice.enabled` route as a compatibility
  fallback.
- Addressable means that Fox may identify the capability, read its registered
  description, or preserve an existing route. It never means that P0 may
  execute the capability from LLM output.
- The app root remains query-only and is reported as needing a Chat Bridge. It
  is never treated as executable merely because it appears in the inventory.

The complete 42-record integrated inventory, entry points, implementation state, and
Bridge gaps are recorded in `CAPABILITY_BOM.md`.

## Security boundary

This path must not call:

- `AminActionDispatcher`;
- `NodeMetadataStore.createCustomNode`;
- Tool, shell, Connector, workflow, GitHub write, merge, release, or production
  operations;
- capability approval, certification, trust upgrade, Agent assignment, or
  autonomy changes.

## Deferred context

- GitHub Work / CI question answering;
- Task context;
- evidence-backed Agent status;
- broader repository and GitHub reusable-implementation search;
- Tool execution and self-extension.

Unavailable sources must be returned in `unresolved_gaps`; the runtime must not
invent Agent, Task, CI, or capability state.

## Fox chat Node and managed Markdown

The existing full-screen and floating LLM chat surfaces share one registered
chat Node named `狐狸` (`app:fox-chat`). They do not create one LLM connection
per Node.

The first managed context uses the existing typed relation vocabulary:

```text
app:fox-chat --reads_from--> app:fox-chat-md
```

Every bundled functional Node has a same-ID Markdown asset under
`node-context/<raw-node-id>.md`. At Registry construction time the asset is
projected as a non-capability `reference` Node and the functional Node receives
a stable `reads_from` relation. Reference Nodes stay outside the Capability BOM
and are hidden from the default Canvas projection so the Graph does not become
a large MD star; the relationship remains visible in Node Inspector.

`NodeMdContextBuilder` accepts Markdown only when the source Node, context Node,
registered `reads_from` relation, and bundled asset all agree. Missing
declarations, relations, sources, or oversized content fail closed as
`unresolved_gaps`. `FoxConversationContextBuilder` selects at most three Nodes
whose registered title or voice alias matches the question, then adds only
those Node MDs to the shared LLM context.

An OWNER-approved custom Node receives an app-private generated MD, a reference
Node, and the same `reads_from` relation during Registry registration. The MD
starts with `review_status: generated`; it is not silently approved. Removing
the custom Node removes its active reference Node and relation while retaining
the app-private MD file as recoverable draft data.

The same context is passed through each provider's supported system-instruction
field. It does not grant Tool execution, Graph/MD mutation, GitHub write,
self-extension, or autonomy. `FoxPresentationBridge` connects the existing
`FloatingVoiceController` to the existing `FoxPetOverlayService` only for
presentation state, full reply bubble, tap-to-listen, and optional Android TTS.
