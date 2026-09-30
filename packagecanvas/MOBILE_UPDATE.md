# PackageCanvas mobile workspace · 20260930.1

Web-only update; Android thin shell and its build workflow are unchanged.

## User-visible behavior

- At widths up to 760px: compact header, explicit graph layer selector, zoom controls, bottom navigation and collapsible editing/workspace panels.
- Browse mode supports touch panning and pinch zoom without moving nodes. Tap nodes/groups to read full Markdown and follow relation/source links.
- Node browser searches titles and Markdown; arrangement is an explicit separate mode.
- GEN2 projection reader is prepared for the header or `?view=gen2`, importing an independent canvas once per browser when projection data becomes available. Until public disclosure is authorized, it displays an explicit unavailable message. Existing canvases are preserved.
- Save-and-reload waits for IndexedDB transaction completion and uses a fresh URL query.
- Desktop menus remain available. Node editor becomes a single-column mobile sheet.

## Data and boundaries

`data/gen2-projection-v0.1.json` currently contains an unavailable marker only. The private full projection is withheld pending explicit authorization for public disclosure. The projection reader was tested locally against the candidate snapshot (128 nodes, 22 groups, 210 links). It is not a live Drive connection. No workflow execution or approval writeback is implemented. Local edits remain in this browser's IndexedDB/localStorage.

## Verification

Chromium mobile emulation at 390×844: direct projection load, one/two-finger gestures, search, Markdown/relations reader, node creation, save/reload persistence and workspace panel passed with no page errors. 360×740 had no document horizontal overflow. At 1280×900 all five desktop menus remained visible. JavaScript parser and projection identity/endpoint/content checks passed. Chinese glyphs were not available in the test host fonts. Physical Android WebView acceptance remains outstanding.

## Rollback

Revert this Web update commit, restoring `packagecanvas/index.html` from `a25d096c482872b65887cb2df044416ca43684ed` and removing the added CSS/data/documentation. IndexedDB name, existing canvas IDs, and Android project are unchanged; rollback does not erase local user canvases.
