import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {webcrypto} from 'node:crypto';
const require=createRequire(import.meta.url);
const Media=require('../living-library/live/media-library.js');

test('v1 media contract is standalone, bounded and cannot accept active files',()=>{
 assert.equal(Media.SCHEMA,'nw.media.asset.v1');
 assert.equal(Media.DB_NAME,'nw-media-library-v1');
 const photo=new Blob(['synthetic'],{type:'image/jpeg'});
 assert.equal(Media.validate({blob:photo,width:480,height:320}).width,480);
 for(const mime of ['image/svg+xml','text/html','application/json']){
  assert.throws(()=>Media.validate({blob:new Blob(['<svg/>'],{type:mime}),width:10,height:10}),{code:'MEDIA_INVALID_IMAGE'});
 }
 assert.throws(()=>Media.validate({blob:photo,width:0,height:3}),{code:'MEDIA_INVALID_IMAGE'});
 assert.throws(()=>Media.validate({blob:photo,width:Media.MAX_EDGE+1,height:3}),{code:'MEDIA_INVALID_IMAGE'});
 assert.throws(()=>Media.validate({blob:new Blob([new Uint8Array(Media.MAX_BYTES+1)],{type:'image/jpeg'}),width:100,height:100}),{code:'MEDIA_IMAGE_TOO_LARGE'});
 assert.throws(()=>Media.checkId('local.card.001'),{code:'MEDIA_INVALID_ID'});
 assert.throws(()=>Media.checkId('http://example.com/image'),{code:'MEDIA_INVALID_ID'});
 assert.equal(Media.checkId('local.media.sha256.'+'a'.repeat(64)),'local.media.sha256.'+'a'.repeat(64));
});

test('unavailable IndexedDB and cryptography fail closed, without creating any browser data',async()=>{
 await assert.rejects(Media.open({indexedDB:null,crypto:webcrypto}),{code:'MEDIA_DB_UNAVAILABLE'});
 await assert.rejects(Media.open({indexedDB:{open(){}},crypto:{}}),{code:'MEDIA_CRYPTO_UNAVAILABLE'});
});

test('scaffold is not injected into live pages and cannot alter the legacy card storage schema',async()=>{
 const pages=['living-library/live/index.html','living-library/live/card-creation.html'];
 for(const path of pages){
  const html=await readFile(new URL('../'+path,import.meta.url),'utf8');
  assert.doesNotMatch(html,/(?:src=["'][^"']*media-library\.js|NWMediaLibrary)/);
 }
 const source=await readFile(new URL('../living-library/live/object-craft.js',import.meta.url),'utf8');
 assert.match(source,/living-library-player-objects-v1/);
 assert.match(source,/dataUrl/);
});
