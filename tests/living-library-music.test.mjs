import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {runInNewContext} from 'node:vm';
import {createRequire} from 'node:module';

const require = createRequire(import.meta.url);
const catalog = require('../living-library/live/public-catalog.js');

function mockPlayer(source) {
  const urls = new Set();
  class Audio {
    constructor(){ this.paused=true; this.ended=false; this.volume=1; this.currentTime=0; this.callbacks={}; }
    addEventListener(name, cb){ this.callbacks[name]=cb; }
    pause(){ this.paused=true; this.callbacks.pause?.(); }
    play(){ this.paused=false; this.ended=false; this.callbacks.play?.(); return Promise.resolve(); }
    load(){}
    removeAttribute(name){if(name==='src')this.src='';}
  }
  const page={localStorage:{getItem(){return null},setItem(){}}};
  runInNewContext(source,{window:page,Audio,navigator:{},URL:{
    createObjectURL(){const ref='blob:music-test-'+(urls.size+1);urls.add(ref);return ref;},
    revokeObjectURL(ref){urls.delete(ref);}
  }});
  return {player:page.LivingMusicPlayer,urls};
}

test('existing System Book cards remain in order, and music is a single additive card',()=>{
  const d=catalog.buildCatalog();
  const original=['public.vault.web','public.canvas.workbench','public.gpt.skills','public.system.ui.map','public.system.controls'];
  assert.deepEqual(d.bookCards.system.slice(0,5),original);
  assert.equal(d.bookCards.system.at(-1),'public.system.music.player');
  assert.equal(d.bookCards.system.filter(id=>id==='public.system.music.player').length,1);
  assert.equal(d.cards['public.system.music.player'].url,'./music-player.html');
  assert.equal(d.bookCards.gaming.includes('public.gba.runtime'),true);
});

test('music page is loaded as a trusted same-origin in-book reader and uses parent audio',async()=>{
  const scene=await readFile(new URL('../living-library/live/index.html',import.meta.url),'utf8');
  const player=await readFile(new URL('../living-library/live/music-player.html',import.meta.url),'utf8');
  assert.match(scene,/new URL\\('\.\\/music-player\\.html',current\\)\\.pathname/);
  assert.match(scene,/'music-runtime\\.js'/);
  assert.match(player,/parent\\.LivingMusicPlayer/);
});

test('parent music engine accepts iframe File objects and keeps audio alive independently of iframe',async()=>{
  const source=await readFile(new URL('../living-library/live/music-runtime.js',import.meta.url),'utf8');
  const {player,urls}=mockPlayer(source);
  assert.ok(player);
  assert.equal(player.snapshot().volume,0.7,'default volume should not start muted');
  const crossRealmAudioFile={name:'my-song.mp3',type:'audio/mpeg',size:512,slice(){}};
  assert.equal(player.selectFiles([crossRealmAudioFile]),true);
  assert.equal(player.snapshot().tracks[0],'my-song.mp3');
  assert.equal(await player.play(),true);
  assert.equal(player.snapshot().playing,true);
  assert.equal(player.selectFiles([{name:'script.js',type:'text/javascript',size:512,slice(){}}]),false);
  assert.equal(player.snapshot().tracks.length,1);
  player.stop();assert.equal(player.snapshot().playing,false);
  assert.equal(urls.size,1,'audio objectURL belongs to the parent, not the iframe');
});
