# Runtime Foundation 0.11.11

Scope for this feature branch:

- Canonical graph entity semantics: `type -> level -> visualSize`.
- Registry remains the single source of graph entities.
- Legacy renderer compatibility so newly created plain NODE entities no longer default to the medium tier.
- Gate state is read from the persisted registered Edge and exposed as `gateState` in the unified graph.
- Minimal registered Edge traversal runtime: registered Edge -> Gate -> Runtime Trace -> target.
- WebView bridge entry: `traverseUnifiedEdge(edgeId)`.

Canonical levels:

- Large: SYSTEM / AGENT
- Medium: STREAM / SKILL / GROUP
- Small: NODE / COMMAND / TOOL

Runtime invariant:

- A missing Edge cannot be traversed.
- A disabled Gate blocks traversal.
- Traversal writes only Runtime Trace and does not create Registry entities or permanent Edges.
