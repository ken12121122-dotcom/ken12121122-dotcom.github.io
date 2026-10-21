import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,access} from 'node:fs/promises';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const root=new URL('../',import.meta.url);
const load=path=>readFile(new URL(path,root),'utf8');
const Catalog=require('../living-library/live/public-catalog.js');
const [shell,page,snapshotScript]=await Promise.all([
 load('living-library/live/index.html'),
 load('living-library/live/gpt-skills.html'),
 load('living-library/live/gpt-skills-catalog.js')
]);
const snapshot=new Function('self',snapshotScript+'return self.GPTSkillsSnapshot;')({});

test('GPT Book adds a real embeddable webpage card, preserving all 14 books and 16 Android routes',()=>{
 const a=Catalog.buildCatalog();
 assert.equal(a.books.length,15);
 assert.equal(a.bookCards.android.length,16);
 assert.equal(a.bookCards.gaming.length,6);
 assert.deepEqual(a.bookCards.gpt,['public.gpt.bridge','public.gpt.skills']);
 const card=a.cards['public.gpt.skills'];
 assert.equal(card.kind,'public_function');
 assert.equal(card.sourceLayer,'public');
 assert.equal(card.status,'public_entry');
 assert.equal(card.nativeRoute,undefined);
 assert.equal(card.url,'./gpt-skills.html');
 assert.equal(a.cards['public.gba.runtime'].nativeRoute,'gba');
 const projected=Catalog.buildCatalog({source:{commit:'a'.repeat(40)},books:[],cards:{}});
 assert.ok(projected.bookCards.gpt.includes('public.gpt.skills'));
});

test('GPT card actually opens through trusted in-book iframe and supports direct navigation',async()=>{
 assert.doesNotMatch(shell,/id="gptSkillsShortcut"/);
 assert.match(shell,/new URL\('\.\/gpt-skills\.html',current\)\.pathname/);
 assert.match(shell,/if\(route\.get\('card'\)==='public\.gpt\.skills'\)/);
 assert.match(shell,/if\(card\)openCardReader\(card\)/);
 assert.match(shell,/readerFrame/);
 assert.match(shell,/readerFrame'\)\.src=card\.url/);
 assert.doesNotMatch(page,/amin-library:\/\//);
 assert.match(page,/某某卡/);
 await access(new URL('living-library/live/gpt-skills.html',root));
 await access(new URL('living-library/live/gpt-skills-catalog.js',root));
});

test('skill card enumerates this session capability SKILL procedures without mislabeling authorization',()=>{
 assert.equal(snapshot.schemaVersion,'GPT-SKILLS-001/1.0');
 assert.equal(snapshot.asOf,'2026-10-14');
 assert.equal(snapshot.skills.length,190);
 assert.equal(new Set(snapshot.skills.map(s=>s.id)).size,190);
 assert.equal(Object.keys(snapshot.groups).length,21);
 assert.equal(snapshot.toolNamespaces.length,36);
 for(const t of ['GitHub','Google_Drive','Gmail','Google_Calendar','Figma','Notion'])assert.ok(snapshot.toolNamespaces.includes(t),t);
 for(const s of snapshot.skills){assert.ok(s.group&&s.name&&s.description);assert.ok(!/(?:github_pat_|sk-proj-)/.test(s.description));}
 assert.match(page,/公開快照/);
 assert.match(page,/不會直接呼叫 ChatGPT 私人對話/);
 assert.match(page,/iframe/);
 assert.match(page,/GEN2/);
});

test('card page scripts parse, skill rendering uses textContent instead of HTML injection',()=>{
 assert.match(page,/x\.textContent=String\(text\)/);
 assert.doesNotMatch(page,/\.innerHTML\s*=/);
 const scripts=[...page.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)];
 assert.ok(scripts.length>=2);
 for(const [i,m] of scripts.entries())assert.doesNotThrow(()=>new Function(m[2]),'inline script '+i);
});

test('future named-card instructions specify web HTML + catalog + iframe + release gate',()=>{
 for(const word of ['預設定位','唯一 ID','HTML','iframe','公開','發布','權限','名稱衝突'])
  assert.ok(page.includes(word),'missing rule: '+word);
 assert.match(page,/普通卡片無需升版 APK/);
});
