(()=>{'use strict';
if(window.AminGitHubBootstrapGateInstalled)return;window.AminGitHubBootstrapGateInstalled=true;
const gate=document.createElement('div');gate.id='githubBootstrapGate';gate.style.cssText='position:fixed;inset:0;z-index:30;background:var(--bg);display:flex;align-items:center;justify-content:center;padding:24px';
gate.innerHTML='<div style="width:min(420px,92vw);background:var(--panel);border:1px solid var(--border);border-radius:18px;padding:18px;box-shadow:0 16px 44px rgba(0,0,0,.14)"><div style="font-size:18px;font-weight:900">正在掃描真實 GitHub 架構</div><div id="githubBootstrapStatus" style="margin-top:8px;color:var(--muted);font-size:12px">準備同步 GitHub…</div><div style="height:8px;background:rgba(127,127,127,.15);border-radius:999px;overflow:hidden;margin-top:14px"><div id="githubBootstrapBar" style="height:100%;width:8%;background:var(--active);transition:width .3s ease"></div></div><div id="githubBootstrapRevision" style="margin-top:10px;color:var(--muted);font-size:10px"></div><button id="githubBootstrapRetry" style="display:none;margin-top:14px;border:1px solid var(--border);background:var(--panel);color:var(--text);border-radius:999px;padding:8px 12px;font-weight:800">重新同步</button></div>';
document.body.appendChild(gate);
let ready=false,last='';
function readState(){try{return JSON.parse(AminWiki.getSourceReviewJson()||'{}')}catch(_){return{}}}
function sync(){const state=readState(),s=state.sync||{},status=String(s.status||'idle');last=status;const text=document.getElementById('githubBootstrapStatus'),bar=document.getElementById('githubBootstrapBar'),rev=document.getElementById('githubBootstrapRevision'),retry=document.getElementById('githubBootstrapRetry');
if(status==='idle'||status==='syncing'){ready=false;gate.style.display='flex';text.textContent=status==='idle'?'準備同步 GitHub…':'讀取 Repository tree，建立 Evidence Candidate…';bar.style.width=status==='idle'?'12%':'64%';retry.style.display='none';rev.textContent='';return}
if(status==='failed'){ready=false;gate.style.display='flex';text.textContent='GitHub 掃描失敗；沒有建立任何正式節點';bar.style.width='100%';rev.textContent=String(s.error||'SYNC_FAILED');retry.style.display='inline-block';return}
if(status==='ready'){ready=true;bar.style.width='100%';text.textContent=state.hasPending?'掃描完成，等待你審核':'掃描完成，沒有新的 Pending 變更';rev.textContent=s.revision?'revision · '+String(s.revision).slice(0,12):'';retry.style.display='none';setTimeout(()=>{gate.style.display='none';try{window.AminSourceReviewRefresh?.()}catch(_){}},220)}}
document.getElementById('githubBootstrapRetry').onclick=()=>{try{AminWiki.syncSourceNow()}catch(_){}};
window.AminGitHubBootstrapGate={sync,getState:()=>({ready,status:last})};sync();setTimeout(sync,300);setInterval(sync,1200);
})();
