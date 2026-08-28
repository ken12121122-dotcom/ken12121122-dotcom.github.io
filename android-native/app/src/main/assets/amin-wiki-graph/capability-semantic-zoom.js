(()=>{'use strict';
if(window.AminCapabilitySemanticZoomInstalled)return;window.AminCapabilitySemanticZoomInstalled=true;
const LARGE=new Set(['SYSTEM','AGENT','ASSISTANT']);
const MEDIUM=new Set(['STREAM','WORKFLOW','SKILL','GROUP','DOMAIN']);
let graph=null,tier='structure',lastMode='',raf=0;
const base=document.getElementById('graph');
const levelText=document.getElementById('levelText');
const legend=document.querySelector('.legend');
const overlay=document.createElement('canvas');
overlay.id='capabilitySemanticZoomOverlay';
overlay.style.cssText='position:fixed;inset:0;width:100%;height:100%;z-index:4;pointer-events:none;display:none';
document.body.appendChild(overlay);
const badge=document.createElement('div');
badge.id='capabilitySemanticZoomBadge';
badge.style.cssText='position:fixed;left:9px;top:124px;z-index:13;background:var(--panel);border:1px solid var(--border);border-radius:999px;padding:6px 9px;font-size:10px;font-weight:800;color:var(--text);display:none;pointer-events:none';
document.body.appendChild(badge);
if(legend)legend.innerHTML='縮小＝大節點<br>中景＝大＋中節點<br>放大＝大＋中＋小節點<br>線＝Connect　🟢＝Gate 可通行';
function clean(v){return String(v||'').trim()}
function canonicalType(e){return clean(e?.canonicalType||e?.canonical_type||e?.nodeType||e?.node_type||e?.type).toUpperCase().replace(/[ -]+/g,'_')}
function visualTier(e){const explicit=clean(e?.visualSize||e?.level).toLowerCase();if(['large','medium','small'].includes(explicit))return explicit;const type=canonicalType(e);if(LARGE.has(type))return'large';if(MEDIUM.has(type))return'medium';const id=clean(e?.id),parent=clean(e?.parentId||e?.parent_id);if(id==='app:app-core'||id==='app-core'||type==='ROOT'||type==='APP_ROOT')return'large';if(!parent||parent==='app:app-core'||parent==='app-core')return'medium';return'small'}
function currentMode(){try{return window.AminGraphArchitectureVisibility?.getState?.().mode||'capability'}catch(_){return'capability'}}
function currentTier(){const label=clean(levelText?.textContent).toLowerCase();if(label.includes('overview'))return'overview';if(label.includes('detail'))return'detail';return'structure'}
function allowed(t){if(tier==='overview')return t==='large';if(tier==='structure')return t==='large'||t==='medium';return true}
function load(){try{const raw=window.AminWiki?.getUnifiedGraphJson?.();if(!raw)return false;const next=JSON.parse(raw);if(!Array.isArray(next.nodes))return false;graph=next;render();return true}catch(_){return false}}
function drawNode(ctx,e,p){const vt=visualTier(e),r=vt==='large'?34:vt==='medium'?25:16;ctx.beginPath();ctx.arc(p.x,p.y,r,0,Math.PI*2);ctx.fillStyle=vt==='large'?'#315f82':vt==='medium'?'#497d62':'#7653bd';ctx.fill();ctx.strokeStyle=getComputedStyle(document.documentElement).getPropertyValue('--panel').trim()||'#fff';ctx.lineWidth=2;ctx.stroke();ctx.fillStyle=getComputedStyle(document.documentElement).getPropertyValue('--text').trim()||'#17211b';ctx.font=`800 ${vt==='large'?14:vt==='medium'?12:10}px sans-serif`;ctx.textAlign='center';ctx.textBaseline='top';ctx.fillText(clean(e.title||e.id).slice(0,24),p.x,p.y+r+6)}
function layout(list,w,h){const out=new Map(),large=list.filter(e=>visualTier(e)==='large'),medium=list.filter(e=>visualTier(e)==='medium'),small=list.filter(e=>visualTier(e)==='small'),cx=w/2,cy=Math.max(250,h*.48);large.forEach((e,i)=>out.set(e.id,{x:cx+(i-(large.length-1)/2)*110,y:cy}));medium.forEach((e,i)=>{const a=(i/Math.max(1,medium.length))*Math.PI*2-Math.PI/2,r=Math.min(w,h)*.28;out.set(e.id,{x:cx+Math.cos(a)*r,y:cy+Math.sin(a)*r})});small.forEach((e,i)=>{const a=(i/Math.max(1,small.length))*Math.PI*2-Math.PI/2,r=Math.min(w,h)*.42;out.set(e.id,{x:cx+Math.cos(a)*r,y:cy+Math.sin(a)*r})});return out}
function render(){cancelAnimationFrame(raf);raf=requestAnimationFrame(()=>{const mode=currentMode();tier=currentTier();lastMode=mode;if(mode!=='capability'||!graph){overlay.style.display='none';badge.style.display='none';return}const label=tier==='overview'?'遠景｜只顯示大節點':tier==='structure'?'中景｜大＋中節點':'近景｜大＋中＋小節點';badge.textContent=label;if(tier==='detail'){overlay.style.display='none';badge.style.display='block';if(base)base.style.opacity='1';return}badge.style.display='block';overlay.style.display='block';if(base)base.style.opacity=tier==='overview'?'.07':'.12';const rect=overlay.getBoundingClientRect(),dpr=Math.max(1,window.devicePixelRatio||1);overlay.width=Math.round(rect.width*dpr);overlay.height=Math.round(rect.height*dpr);const ctx=overlay.getContext('2d');ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,rect.width,rect.height);const nodes=graph.nodes.filter(e=>e&&e.entityType!=='command'&&allowed(visualTier(e))),ids=new Set(nodes.map(e=>e.id)),pos=layout(nodes,rect.width,rect.height);ctx.strokeStyle=getComputedStyle(document.documentElement).getPropertyValue('--line').trim()||'#8aa79a';ctx.globalAlpha=.46;ctx.lineWidth=1.25;for(const r of graph.relations||[]){if(!ids.has(r.from)||!ids.has(r.to))continue;const a=pos.get(r.from),b=pos.get(r.to);if(!a||!b)continue;ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.lineTo(b.x,b.y);ctx.stroke()}ctx.globalAlpha=1;for(const e of nodes){const p=pos.get(e.id);if(p)drawNode(ctx,e,p)}})}
function sync(){const mode=currentMode(),nextTier=currentTier();if(mode!==lastMode||nextTier!==tier)render()}
const observer=new MutationObserver(sync);if(levelText)observer.observe(levelText,{childList:true,subtree:true,characterData:true});
for(const id of['zin','zout','fit','reset'])document.getElementById(id)?.addEventListener('click',()=>setTimeout(sync,40),true);
document.getElementById('architectureLayers')?.addEventListener('click',()=>setTimeout(sync,50),true);
window.addEventListener('resize',render);
const originalReload=window.AminReloadUnifiedGraph;window.AminReloadUnifiedGraph=function(){let result=false;try{result=originalReload?originalReload.apply(this,arguments):false}catch(_){}setTimeout(load,140);return result};
window.AminCapabilitySemanticZoom={load,render,getState:()=>({mode:currentMode(),tier,currentLevel:clean(levelText?.textContent),counts:(graph?.nodes||[]).reduce((a,e)=>{const t=visualTier(e);a[t]=(a[t]||0)+1;return a},{})}),classify:visualTier,selfTest:()=>({system:visualTier({canonicalType:'SYSTEM'})==='large',skill:visualTier({canonicalType:'SKILL'})==='medium',node:visualTier({canonicalType:'NODE',parentId:'x'})==='small',rootFallback:visualTier({id:'app:app-core'})==='large'})};
load();setInterval(sync,250);
})();
