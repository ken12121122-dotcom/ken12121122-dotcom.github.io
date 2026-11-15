# 換衣世界人物資產

| 檔案 | Bytes | SHA-256 |
|---|---:|---|
| `anna_phone_v1.glb` | 10,796,376 | `8c63642059acba37dc00f000ba44c585f60a31e1c2891c763032f6f5e55e0041` |

## Attribution — 必須保留

| | |
|---|---|
| Title | Anna Ipati (animated) |
| Author | Jungle Jim — https://sketchfab.com/jungle_jim |
| Source | https://sketchfab.com/3d-models/anna-ipati-animated-9ef7dbea47854f49b496e8c0a8332157 |
| Licence | **CC-BY-4.0** — http://creativecommons.org/licenses/by/4.0/ |

`anna_phone_v1.glb` 是衍生版（已修改），不是原作者的原檔。換衣世界畫面上會顯示同一份作者標示。

## 來源與產生方式

- 母檔：`assets/3d/anna_ipati_animated.glb`（用 `assets/3d/join.sh` 還原，SHA-256 `0eb5a6bd…8aab`，未修改）。
- 產生腳本：`tools/characters/anna-phone-build.mjs`（gltf-transform 4.x + meshoptimizer + sharp）：
  `node anna-phone-build.mjs anna_ipati_animated.glb anna_phone_v1.glb 1024`

## 與母檔的差異（刻意取捨）

| 項目 | 母檔 | 手機版 |
|---|---|---|
| 大小 | 344 MB | 10.8 MB |
| 骨架 | 102 joints | 102 joints（每個網格各一份 skin，節點相同） |
| 骨骼動畫 | 302 TRS channels，約 17.97 s | 302 TRS channels，約 17.95 s（resample） |
| 表情 Morph | 349 FACS targets + 19 weights channels | **移除**（臉部表情不會動） |
| 貼圖 | 74 張，131 MB | 38 張 WebP，≤1024 px，約 5.8 MB |
| 壓縮 | 無 | `EXT_meshopt_compression`、`KHR_mesh_quantization`、`EXT_texture_webp`（皆為 required） |

動畫是原地走路循環；換衣世界以 0.2 s 那一格當站姿，走路時播放循環。
