/* 開場畫面（Start Gate）— 像遊戲一樣：標題、目前角色、▶ 開始。
 * 開場時不再自動跳出更新公告或雲端解鎖密碼框：兩者改成標題畫面上的小提示，
 * 玩家想看才點。每個分頁工作階段只出現一次；自動更新重新載入不會再擋一次。
 * 只管「進入遊戲」的流程，不碰存檔、契約、掃描或授權資料本身。 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingStartGateKit=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
const SESSION_KEY='nw-start-gate-started-v1';
const TIPS=Object.freeze([
 '點人物開啟語音，唸「ひゃくめんそう」進入換衣世界',
 '在任何世界唸「てんいかいじょ」都能回到圖書館',
 '點人物開啟語音後唸「Book」打開卡冊',
 '左手拖曳搖桿走動，右手拖曳畫面轉動視角'
]);
/* Deep links (open a book/card) and the release-proof view go straight in. */
function shouldShow(search,session){
 let q;try{q=new URLSearchParams(search||'')}catch{q=new URLSearchParams()}
 if(q.has('book')||q.has('card')||q.get('verify')==='1')return false;
 try{if(session?.getItem(SESSION_KEY)==='1')return false}catch{}
 return true;
}
function markStarted(session){try{session?.setItem(SESSION_KEY,'1');return true}catch{return false}}
function updateText(items){const n=Array.isArray(items)?items.length:0;return n?'書庫有 '+n+' 項更新':'書庫已是最新版本'}
function tipAt(index){return TIPS[((index%TIPS.length)+TIPS.length)%TIPS.length]}
/* State only; the page wires DOM. Notices that arrive while the gate is up are held, never popped. */
function createGate({visible=true,onStart=()=>{},toast=()=>{}}={}){
 let open=!!visible,update=null,vault=null;const listeners=new Set();
 const emit=()=>{for(const fn of listeners)try{fn(api.view())}catch{}};
 const api={
  get open(){return open},
  view(){return {open,update:update?{count:update.items.length,text:updateText(update.items),explicit:update.explicit}:null,vault:!!vault}},
  subscribe(fn){listeners.add(fn);fn(api.view());return()=>listeners.delete(fn)},
  /* An update the player explicitly asked for (reconnect) is shown after start; others wait behind a chip. */
  holdUpdate(items,show,explicit=false){
   if(!open){if(explicit)show();else if(items?.length)toast('✦ '+updateText(items)+'・可在系統之書查看');return 'toast'}
   update={items:Array.isArray(items)?items:[],show,explicit};emit();return 'held';
  },
  openUpdate(){if(update?.show)update.show();},
  /* A saved cloud credential is offered as a chip on the title screen only — never a startup modal. */
  offerVault(present){if(!open)return 'ignored';vault={present};emit();return 'offered'},
  openVault(){vault?.present?.()},
  start(){
   if(!open)return false;open=false;const pending=update;update=null;vault=null;emit();
   onStart();if(pending?.explicit)pending.show();return true;
  }
 };
 return api;
}
return Object.freeze({SESSION_KEY,TIPS,shouldShow,markStarted,updateText,tipAt,createGate});
});
