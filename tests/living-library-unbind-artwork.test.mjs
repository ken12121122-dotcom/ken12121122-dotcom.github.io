import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Artwork=require('../living-library/live/unbind-card-art.js');
const ATLAS='local.atlas.compendium';
const card=(id,title,image=null,more={})=>({
 id,objectType:'card',templateId:'blank-card-v1',status:'draft',
 placement:'hover',ownerBookId:null,containerId:null,slotIndex:null,
 title,description:'original text',cardType:'法術',image,cardNumber:'',linkedCardId:null,
 revision:1,createdAt:'t0',updatedAt:'t0',...more
});
const blank=()=>card('local.card.blank','空白卡片',null,{
 description:'',cardType:'',placement:'book',ownerBookId:ATLAS,containerId:ATLAS,slotIndex:9
});
const store=(objects=[],extra={})=>{
 const data=new Map([[Craft.KEY,JSON.stringify({version:Craft.VERSION,objects,...extra})]]);
 return {
  getItem:key=>data.get(key)??null,
  setItem:(key,value)=>{data.set(key,value)},
  get cards(){return JSON.parse(data.get(Craft.KEY)).objects},
  get raw(){return data.get(Craft.KEY)},
  backup(){return data.get(Craft.UNBIND_ART_BACKUP_KEY)}
 };
};

test('selected second illustration is embedded as a valid compact JPG, not an empty placeholder',()=>{
 const data=Artwork.image.dataUrl;
 assert.match(data,/^data:image\/jpeg;base64,\/9j\//);
 assert.ok(data.length<70000);
 assert.equal(Buffer.from(data.split(',')[1],'base64')[0],0xff);
 assert.equal(Buffer.from(data.split(',')[1],'base64')[1],0xd8);
 assert.equal(Artwork.id,'unbind-contract-user-selected-art-v1');
});
test('GBA shows selected second illustration on built-in unbind recipe and downloads same image to a distinct blank-card ID',()=>{
 const db=store([blank()]);
 const sources=Craft.listDownloadableTemplates(db);
 const source=sources.find(c=>c.id===Craft.UNBIND_BLUEPRINT_ID);
 assert.ok(source);
 assert.equal(source.title,'解除契約卡');
 assert.deepEqual(source.image,Artwork.image);
 const made=Craft.forgeBlankFromTemplate(db,{
  blankId:'local.card.blank',blankRevision:1,
  sourceId:source.id,sourceRevision:source.revision
 },()=> 't1');
 assert.equal(made.id,'local.card.blank');
 assert.equal(made.title,'解除契約卡');
 assert.deepEqual(made.image,Artwork.image);
 assert.equal(made.downloadTemplate,false);
 assert.equal(made.cardNumber,'');
 assert.equal(made.copyInfo.sourceId,Craft.UNBIND_BLUEPRINT_ID);
 assert.equal(db.cards.length,1,'blueprint must not create an extra physical card');
 assert.equal(db.cards[0].id,'local.card.blank');
});
test('restore missing image only on live unbinding spells and backup entire raw save before changing photo',()=>{
 const original=card('local.card.original','解除契約卡');
 const unrelated=card('local.card.other','旅人卡');
 const spent=card('local.card.spent','解除契約卡',null,{placement:'contained',
  containment:{kind:'consumed_spell',holderType:'card',holderId:'local.card.other'}});
 const db=store([original,unrelated,spent]),before=db.raw;
 assert.deepEqual(Craft.restoreMissingUnbindImages(db,()=> 't2'),[original.id]);
 assert.equal(db.backup(),before);
 assert.deepEqual(db.cards[0].image,Artwork.image);
 assert.equal(db.cards[0].revision,2);
 assert.equal(db.cards[0].title,'解除契約卡');
 assert.equal(db.cards[0].id,original.id);
 assert.deepEqual(db.cards[1],unrelated);
 assert.deepEqual(db.cards[2],spent);
 const once=db.raw;
 assert.deepEqual(Craft.restoreMissingUnbindImages(db),[]);
 assert.equal(db.raw,once,'idempotent repairs do not rewrite localStorage');
});
test('never overwrite a player's own existing card photo or system recipe artwork',()=>{
 const custom={dataUrl:'data:image/jpeg;base64,/9j/AA==',scale:2,x:21,y:72};
 const old=card('local.card.original','解除契約卡',custom);
 const db=store([old],{recipeArtwork:custom}),before=db.raw;
 assert.deepEqual(Craft.restoreMissingUnbindImages(db),[]);
 assert.equal(db.raw,before);
 assert.equal(db.backup(),undefined);
 assert.deepEqual(Craft.listDownloadableTemplates(db)[0].image,custom);
});
test('backup write failure leaves every original field unchanged, including absent image',()=>{
 const original=card('local.card.original','解除契約術');
 const db=store([original]),before=db.raw;
 const blocked={getItem:db.getItem,setItem(key,value){
  if(key===Craft.UNBIND_ART_BACKUP_KEY)throw Error('quota');
  db.setItem(key,value);
 }};
 assert.throws(()=>Craft.restoreMissingUnbindImages(blocked),/quota/);
 assert.equal(db.raw,before);
 assert.deepEqual(db.cards,[original]);
});
test('all loading points supply the illustration before object-craft and before showing the cards',async()=>{
 const scene=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
 const editor=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 const model=await readFile(new URL('../living-library/live/atlas-model.js',import.meta.url),'utf8');
 const runtime=await readFile(new URL('../living-library/live/atlas-runtime.js',import.meta.url),'utf8');
 assert.match(scene,/'unbind-card-art.js','object-craft.js'/);
 assert.match(scene,/ObjectCraft\.restoreMissingUnbindImages\(localStorage\)/);
 assert.ok(editor.indexOf('src="./unbind-card-art.js')<editor.indexOf('src="./object-craft.js'));
 assert.match(editor,/Craft\.restoreMissingUnbindImages\(localStorage\)/);
 assert.match(model,/const preview=mode==='download'&&\(download.items\|\|\[\]\)\[download.index\]\|\|card/);
 assert.match(runtime,/craft\.forgeBlankFromTemplate\(storage,selection\)/);
});
