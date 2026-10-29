/**
 * Anna static preview adapter for the existing NW v0.7.2 Three.js scene.
 * No renderer, animation mixer, card contract, or save writes are created.
 * Caller supplies its existing THREE, scene, and GLTFLoader instance.
 * This file deliberately does not silently load an unverified remote asset.
 */
export async function attachAnnaStaticPreview({
  THREE, scene, loader, assetUrl, expectedSha256 = null,
  onProgress = () => {}, scale = 1, position = [0, 0, 0]
}) {
  if (!THREE || !scene?.add || !loader?.loadAsync || !assetUrl) {
    throw new Error("Anna preview requires THREE, the existing scene, GLTFLoader, and a verified assetUrl");
  }
  const root = new THREE.Group();
  root.name = "NW_ANNA_STATIC_PREVIEW";
  root.userData = { characterId: "anna", previewOnly: true, contractEnabled: false,
    sourceSha256: expectedSha256, assetUrl, scanProtected: true, scanObjectId: null };
  let gltf;
  try {
    gltf = await loader.loadAsync(assetUrl, onProgress);
    const character = gltf.scene;
    if (!character) throw new Error("GLB contains no scene");
    character.traverse(obj => {
      if (obj.isMesh || obj.isSkinnedMesh) {
        obj.frustumCulled = false;
        obj.castShadow = false;
        obj.receiveShadow = false;
      }
    });
    character.userData = { ...character.userData, scanProtected: true, contractEnabled: false };
    root.add(character);
    root.position.set(...position);
    root.scale.setScalar(scale);
    scene.add(root);
    return {
      root, character, animationsPresent: gltf.animations?.length ?? 0,
      dispose() {
        scene.remove(root);
        root.traverse(obj => {
          if (obj.geometry) obj.geometry.dispose();
          if (obj.material) {
            for (const mat of (Array.isArray(obj.material) ? obj.material : [obj.material])) {
              // Shared materials and textures may be referenced by other cards;
              // leave disposal to the host resource manager.
              void mat;
            }
          }
        });
      }
    };
  } catch (err) {
    scene.remove(root);
    throw err;
  }
}
