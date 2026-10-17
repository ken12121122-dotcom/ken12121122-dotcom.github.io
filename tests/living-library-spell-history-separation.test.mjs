import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Craft=require('../living-library/live/object-craft.js');
const Catalog=require('../living-library/live/public-catalog.js');

function storage(records=[]){
 let raw=JSON.stringify({version:Craft.VERSION,objects:records});
 return {getItem:key=>key===Craft.KEY?raw:null,
  setItem:(key,value)=>{if(key!==Craft.KEY)throw Error('unknown key');raw=String(value)},
  get raw(){return raw}};
}
function card(i,extra={}){
 return {id:'local.card.'+i,objectType:'card',templateId:'blank-card-v1',
  title:'解除綁定'+i,status:'draft',placement:'hover',revision:1,
  description:'',cardNumber:'',cardType:'',image:null,containerId:null,
  ownerBookId:null,ability:null,permissions:[],...extra};
}
const consumed=(i,time='2026-10-16T16:00:00.000Z')=>
 card(i,{placement:'contained',consumedBy:'local.card.target',
  containment:{kind:'consumed_spell',holderId:'local.card.target',holderType:'card',
   state:'stored',absorbedAt:time}});
const random=()=>{let i=100;return {randomUUID:()=>String(++i).padStart(36,'0')}};

test('B: only consumed spell cards leave creator; their original records remain untouched',()=>{
 const records=[card('blank',{title:'空白卡片'}),card('book',{placement:'book',containerId:'local.atlas.collection',slotIndex:1}),
  consumed('oldA'),consumed('oldB','2026-10-17T13:00:00.000Z'),card('rune',{placement:'contained',
   containment:{kind:'rune_contract',state:'stored',holderId:'local.rune.origin'}})];
 const db=storage(records),before=db.raw;
 assert.deepEqual(Craft.listCreatorCards(db).map(x=>x.id),['local.card.blank','local.card.book','local.card.rune']);
 assert.deepEqual(Craft.listSpellHistory(db).map(x=>x.id),['local.card.oldB','local.card.oldA']);
 assert.equal(Craft.listCards(db).length,5,'internal canonical records still include consumed cards');
 assert.equal(Craft.getStats(db).totalCards,3);
 assert.equal(Craft.getStats(db).inBooks,1);
 assert.equal(db.raw,before,'selection is read-only');
 const saved=JSON.parse(db.raw).objects;
 assert.equal(saved.find(x=>x.id==='local.card.oldA').containment.kind,'consumed_spell');
 assert.equal(saved.find(x=>x.id==='local.card.oldA').consumedBy,'local.card.target');
});

test('consumed history does not exhaust creator 64-card limit; old IDs never reused',()=>{
 const records=Array.from({length:64},(_,i)=>i<4?consumed(String(i)):card(String(i)));
 const db=storage(records),before=JSON.parse(db.raw);
 assert.equal(Craft.listCards(db).length,64);
 assert.equal(Craft.listCreatorCards(db).length,60);
 const result=Craft.createBlankCard(db,random());
 assert.equal(result.title,'空白卡片');
 assert.equal(Craft.getStats(db).totalCards,61);
 assert.equal(Craft.listSpellHistory(db).length,4);
 assert.equal(Craft.listCards(db).length,65);
 const now=JSON.parse(db.raw).objects;
 assert.deepEqual(now.slice(0,64),before.objects,'old records remain byte-equivalent JSON data');
 const filled=storage(Array.from({length:64},(_,i)=>card('active'+i)));
 const frozen=filled.raw;
 assert.throws(()=>Craft.createBlankCard(filled,random()),/上限 64 張/);
 assert.equal(filled.raw,frozen);
});

test('recycled cards remain only in recycle bin; history is not a second inventory',()=>{
 const db=storage([consumed('done'),card('trash',{status:'recycled',placement:'recycled'}),
   card('normal')]);
 assert.equal(Craft.listCreatorCards(db).length,1);
 assert.equal(Craft.listRecycledCards(db).length,1);
 assert.equal(Craft.listSpellHistory(db).length,1);
 assert.equal(Craft.floatingCards(db).length,1);
 assert.equal(Craft.canDownloadTemplate(Craft.listSpellHistory(db)[0]),true,
  'approved GBA appearance-template policy remains independent');
});

test('Game Book includes an independent, readonly, script-safe history destination',async()=>{
 const {cards,bookCards}=Catalog.buildCatalog();
 assert.equal(cards['public.gaming.spell.history'].url,'./spell-history.html');
 assert.equal(cards['public.gaming.spell.history'].number,'16');
 assert.ok(bookCards.gaming.includes('public.gaming.spell.history'));
 const creator=await readFile(new URL('../living-library/live/card-creation.html',import.meta.url),'utf8');
 assert.match(creator,/const cards=\(\)=>Craft\.listCreatorCards\(localStorage\)/);
 assert.doesNotMatch(creator,/Craft\.listSpellHistory/);
 const history=await readFile(new URL('../living-library/live/spell-history.html',import.meta.url),'utf8');
 assert.match(history,/Craft|craft\.listSpellHistory\(localStorage\)/);
 assert.match(history,/\.textContent=/);
 assert.doesNotMatch(history,/\.innerHTML\s*=/);
 assert.doesNotMatch(history,/\.setItem\(|restoreCard\(|recycleCard\(/);
 assert.match(history,/已消耗法術/);
});
