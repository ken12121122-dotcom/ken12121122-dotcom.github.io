// Writes <packDir>/manifest.json from the files actually present (bytes + SHA-256 are measured, never typed).
// Usage: node make-pack-manifest.mjs <packDir> <meta.json>
import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const [dir,metaPath]=process.argv.slice(2);const meta=JSON.parse(fs.readFileSync(metaPath,'utf8'));
const file=(role,name,type)=>{const b=fs.readFileSync(path.join(dir,name));return {role,path:name,type,bytes:b.length,sha256:crypto.createHash('sha256').update(b).digest('hex')}};
const manifest={schema:'nw-character-pack-v1',...meta,files:[file('model',meta.model,'model/gltf-binary'),file('thumbnail',meta.thumbnail,'image/webp')]};
delete manifest.model;delete manifest.thumbnail;
fs.writeFileSync(path.join(dir,'manifest.json'),JSON.stringify(manifest,null,1)+'\n');console.log(JSON.stringify(manifest.files));
