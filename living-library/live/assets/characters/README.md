# 角色包（Character Packs）

換衣世界的人物以**角色包**提供：玩家在換衣世界按 ⬇ 才下載，下載一次存在手機（Cache Storage），之後離線也能載入；有新版本時顯示更新，🗑 可刪除。原本的守書人內建在程式裡，永遠可用。

格式 `nw-character-pack-v1`（`living-library/live/character-packs.js` 驗證）：

```
packs/<id>/
  manifest.json   schema、id、version、name、display、credit、files[{role,path,type,bytes,sha256}]
  <model>.glb     meshopt 網格 + KTX2/Basis 貼圖（GPU 內保持壓縮）
  thumb.webp      縮圖
```

下載時逐檔比對位元組數與 SHA-256，全部通過才寫入；更新失敗保留舊版。同一份 manifest 將來給 App 原生下載共用。

## anna.ipati.v1

| 檔案 | Bytes | SHA-256 |
|---|---:|---|
| `anna.glb` | 19,515,752 | `709fe5120e0d24d43bb7fbfd439d2419d3f13feb38bc92d9036974daaf6b8685` |
| `thumb.webp` | 4,300 | `c5ed3077533d42cc0d28c5495ba0eb2b894ead8173d59664ad92db979fe2c64c` |

### Attribution — 必須保留

| | |
|---|---|
| Title | Anna Ipati (animated) |
| Author | Jungle Jim — https://sketchfab.com/jungle_jim |
| Source | https://sketchfab.com/3d-models/anna-ipati-animated-9ef7dbea47854f49b496e8c0a8332157 |
| Licence | **CC-BY-4.0** — http://creativecommons.org/licenses/by/4.0/ |

這是**已修改**的衍生版，不是原作者原檔。換衣世界畫面顯示同一份作者標示。

### 與母檔差異

| 項目 | 母檔 `assets/3d/anna_ipati_animated.glb` | 角色包 |
|---|---|---|
| 檔案 | 344 MB | 19.5 MB |
| 貼圖顯示卡記憶體（估算） | 約 970 MB（解壓 RGBA） | 約 49 MB（KTX2，最壞 8 bpp） |
| 貼圖 | 74 張，最大 4096 px | 38 張 KTX2：法線 UASTC，其餘 ETC1S；1024 / 512 px |
| 骨架／動畫 | 102 joints；302 骨骼 channels | 相同（每個網格一份 skin、節點相同；resample） |
| 表情 Morph | 349 targets + 19 weights channels | **移除**（臉部不會動） |

### 重建

```sh
assets/3d/join.sh
node tools/characters/build-character-pack.mjs assets/3d/anna_ipati_animated.glb <pack>/anna.glb
node tools/characters/make-pack-manifest.mjs <pack> <meta.json>   # bytes 與 SHA-256 一律量測
```
