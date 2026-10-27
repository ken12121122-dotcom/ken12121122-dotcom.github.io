# Anna Ipati (animated) — source model + handoff notes

`anna_ipati_animated.glb` — 344,425,524 bytes, committed as **four plain parts**.

```sh
./assets/3d/join.sh      # cats the parts together and verifies the checksum
```

```
SHA256  0eb5a6bd0fce27759bcfcea1ac894063f5431bade4742e12e4a80212b2b18aab
```

| Part | Bytes |
|---|---|
| `anna_ipati_animated.glb.part0` | 90,177,536 |
| `anna_ipati_animated.glb.part1` | 90,177,536 |
| `anna_ipati_animated.glb.part2` | 90,177,536 |
| `anna_ipati_animated.glb.part3` | 73,892,916 |

It is split because GitHub hard-rejects any single file over 100 MiB on a normal push,
and the two normal ways around that were both closed off: release-asset upload is not
permitted for the session that committed this, and Git LFS could not reach its storage
host (`github-cloud.s3.amazonaws.com`, 403 at the network policy). Plain sub-100 MiB
parts were the only route that worked. `join.sh` reproduces the original byte for byte —
the checksum above is verified on rejoin, not just copied from the source.

Textures account for 131 MB of the 344 MB; the remaining ~212 MB is geometry and the 349
morph targets. That is why there is no single-file slimmed version — downscaling every
texture still leaves it well over the limit.

---

## Attribution — required, not optional

| | |
|---|---|
| Title | Anna Ipati (animated) |
| Author | Jungle Jim — https://sketchfab.com/jungle_jim |
| Source | https://sketchfab.com/3d-models/anna-ipati-animated-9ef7dbea47854f49b496e8c0a8332157 |
| Licence | **CC-BY-4.0** — http://creativecommons.org/licenses/by/4.0/ |

Read verbatim from the file's own `asset.extras`. CC-BY-4.0 allows redistribution and
modification but **requires** this credit to travel with the file and with every
derivative. This repository is public, so the credit has to stay here.

---

## What is in the file

glTF 2.0, generator `Sketchfab-0.6.0`. 33 meshes, 162 nodes, 1 skin, 1 animation, 74 images.

Blender's glTF importer names each object after its mesh, so the numeric names below are
the object names you will be working with.

### Head and face — this is the clean, complete head

| Object | Verts | Morphs | Material |
|---|---|---|---|
| `1` | 4,354 | 349 | `Std_Skin_Head` |
| `6` | 748 | 349 | `Std_Eyelash` |
| `0` | 331 | 30 | `Std_Tongue` |
| `11` / `12` | 1,403 / 1,474 | 2 | upper / lower teeth |
| `7` / `8` | 165 / 165 | 4 | right eyeball / cornea |
| `9` / `10` | 165 / 165 | 4 | left eyeball / cornea |
| `13` / `14` | 98 / 98 | 82 | tearlines R / L |
| `15` / `16` | 91 / 91 | 82 | eye occlusion R / L |
| `17` / `18` | 5,192 / 1,796 | 395 | fine / thick lash cards |

### Body

| Object | Verts | Morphs | Material |
|---|---|---|---|
| `2` | 2,060 | 349 | `Std_Skin_Body` (torso) |
| `3` | 4,738 | 349 | `Std_Skin_Arm` |
| `4` | 2,590 | 349 | `Std_Skin_Leg` |
| `5` | 1,112 | 349 | `Std_Nails` |

### Clothing

| Object | Verts | Material |
|---|---|---|
| `Bra_Bra_0` | 666 | `material` |
| `Underwear_Bottoms_Underwear_Bottom` | 845 | `Underwear_Bottoms` |

### Hair — 12 separate card groups, not one mesh

`Long_wavy_hair_Shape_*`: `TopOverFine` 35,078 · `BackLoose` 54,991 · `SideTwistFine`
29,447 · `FrontLeft` 30,590 · `BackFine` 17,810 · `BackFineL` 16,850 · `BackPullFine`
12,073 · `BackPullFill` 10,481 · `SidesTwist` 6,988 · `TopPartFine` 4,862 · `Scalp`
2,622 · `BackTwist` 2,355. Every hair material name ends in `_Transparency`, which is a
reliable way to select the whole hair set.

---

## Things that cost us time — read before you start

**The 349 morph targets are a FACS expression set, not face-shape sliders.** Blink, brow,
jaw and viseme pairs, max displacement 0.02–0.05. The names are stripped in this file
(`target_0` … `target_348`), so you cannot find a "jaw width" slider by searching. Any
face-shape change has to be direct deformation of the mesh.

**Shape keys store absolute positions.** Deform the base mesh and you must apply the same
displacement to every one of the 349 key blocks, or every expression snaps back to the
old shape when it fires.

**Do not add vertices with bmesh on a mesh that has shape keys.** bmesh does not extend
the key arrays — it leaves them at the old length, silently — and the glTF exporter then
truncates the mesh back to the old vertex count, so your new geometry vanishes from the
export with no error. `bpy.ops.object.join()` extends the arrays correctly; use that.

**Export morph normals off, or the file triples in size.** `export_morph_normal=False`,
`export_morph_tangent=False` took one export from 87 MB to 20.5 MB with no visible
difference.

**A glTF armature carries a Y-up→Z-up rotation.** Any new object parented to it needs
`ob.matrix_parent_inverse = arm.matrix_world.inverted()` or it renders in the wrong place
— or not at all.

**Deform every part of the head through one shared field.** Narrowing the jaw and lips
while leaving the teeth and tongue alone makes them spear straight out through the mouth.
Objects `1, 6, 11, 12, 0, 13, 14, 15, 16` must all go through the same function;
the eyeballs (`7`–`10`) are scaled rigidly about their own centres so they stay spherical.

**The coordinate conventions.** Blender `(x, y, z)` → glTF `(x, z, −y)`. Forward is `+Z`
in glTF, `−Y` in Blender.

---

## Working setup that worked

Headless Blender as a Python module, no GUI needed:

```bash
pip install --break-system-packages bpy      # 5.2.3, needs Python 3.13
```

```python
import bpy
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath="assets/3d/anna_ipati_animated.glb")
arm = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
arm.data.pose_position = 'REST'      # measure in rest pose, not the animated frame
```

Import of this file takes a few minutes and a few GB of RAM. Save a `.blend` once the
import is done and reload from that instead of re-importing on every iteration.

For checking results, orthographic renders with `film_transparent = True` give an exact
alpha silhouette you can measure, which is far more reliable than eyeballing a shaded
render.

---

## Honest status

Nothing in this file has been modified. It is the untouched original.

Attempts so far at face shape, bust shape and hairstyle were all done on a different,
earlier, damaged export (a "polished v0.3" whose head was 884 disconnected fragments) and
none of them produced a result the owner accepted. The failures clustered entirely on
work that required judging organic form by eye. The operations that did succeed were the
ones with a measurable right answer — preserving the rig and the 349 morphs through
export, finding a boundary-loop walk that only went one direction, finding the shape-key
truncation above.

Plan accordingly: scripted deformation reaches a plausible shape quickly and then stalls,
because nothing in the loop can tell whether the face actually looks right. A human
driving sliders in the Blender GUI, with scripting for the mechanical parts, gets there
and a fully scripted loop does not.
