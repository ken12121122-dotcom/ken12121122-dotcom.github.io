import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const Portal=require('../living-library/live/world-portal.js');
const Voice=require('../living-library/live/atlas-voice.js');
function world(id,name,patterns,log){let open=false;return {id,name,spell:{name:patterns[0],patterns},isOpen:()=>open,
 enter(){open=true;log.push('enter:'+id)},leave(){open=false;log.push('leave:'+id)}}}

test('one return spell leaves any world; entry spells never toggle back out',()=>{
 const log=[],casts=[],p=Portal.createPortal({onCast:c=>casts.push([c.kind,c.spell,c.already])});
 const dress=p.register(world('wardrobe','換衣世界',['百面相','ひゃくめんそう'],log));
 p.register(world('garden','花園',['花鳥風月'],log));
 assert.equal(p.back(),'home');assert.equal(p.current,null);
 assert.equal(p.enter('wardrobe'),'entered');assert.equal(p.current,dress);
 assert.equal(p.enter('wardrobe'),'already','saying the entry spell again keeps you there');assert.equal(p.current,dress);
 assert.equal(p.enter('garden'),'entered');assert.equal(p.current.id,'garden');
 assert.deepEqual(log,['enter:wardrobe','leave:wardrobe','enter:garden'],'switching worlds closes the previous one first');
 assert.equal(p.back(),'returned');assert.equal(p.current,null);assert.equal(p.enter('nope'),'unknown');
 assert.deepEqual(casts.map(c=>c[0]+':'+c[2]),['return:true','enter:false','enter:true','enter:false','return:false']);
 assert.equal(casts.at(-1)[1],Portal.RETURN_SPELL.name);
});

test('registration rejects bad ids, missing hooks and spell collisions (including the return spell)',()=>{
 const p=Portal.createPortal(),log=[];p.register(world('wardrobe','x',['百面相'],log));
 assert.throws(()=>p.register(world('wardrobe','x',['別的'],log)),/已登記/);
 assert.throws(()=>p.register(world('Bad Id','x',['別的'],log)),/id/);
 assert.throws(()=>p.register(world('other','x',['百面相'],log)),/重複/);
 assert.throws(()=>p.register(world('other','x',['転移解除'],log)),/重複/);
 assert.throws(()=>p.register({...world('other','x',['別的'],log),leave:null}),/leave/);
 assert.throws(()=>p.register({...world('other','x',[],log)}),/咒語/);
});

test('transition hook wraps the swap; book is blocked inside any world',()=>{
 const log=[],seen=[],p=Portal.createPortal({onTransition:(kind,swap)=>{seen.push(kind);swap()}});
 p.register(world('wardrobe','x',['百面相'],log));
 let books=0;const cmds=p.voiceCommands({book:{patterns:['book'],run:()=>books++}});
 assert.deepEqual(Object.keys(cmds).sort(),['book','return','world-wardrobe']);
 cmds.book.run();assert.equal(books,1);cmds['world-wardrobe'].run();cmds.book.run();assert.equal(books,1,'Book ignored while in a world');
 cmds.return.run();assert.deepEqual(seen,['enter','return']);assert.equal(p.current,null);
});

test('voice end to end: entry spell, repeated entry, spaced return spell, Book outside only',()=>{
 let now=1000,books=0;const sent=[],native={postMessage:x=>sent.push(JSON.parse(x))},log=[];
 const p=Portal.createPortal();p.register(world('wardrobe','x',['百面相','ひゃくめんそう'],log));
 const v=Voice.create({host:{LivingLibraryNativeVoice:native},now:()=>now,lang:'ja-JP',commands:p.voiceCommands({book:{patterns:['book','ブック'],run:()=>books++}})});
 v.start();const session=sent.at(-1).session;const say=t=>{now+=1000;native.onmessage({data:JSON.stringify({v:1,session,status:'result',text:t})})};
 say('ひゃくめんそう');assert.equal(p.current?.id,'wardrobe');say('百面相');assert.equal(p.current?.id,'wardrobe');
 say('ブック');assert.equal(books,0);say('転移 解除');assert.equal(p.current,null);say('てんいかいじょ');assert.equal(p.current,null);
 say('Book');assert.equal(books,1);
 for(const t of Portal.RETURN_SPELL.patterns){p.enter('wardrobe');say(t);assert.equal(p.current,null,t)}
});
