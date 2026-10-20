/* Three.js model derived from the user supplied v0.2 sandbox. No private data. */
(function(root){'use strict';
root.LivingAtlasModel={create(THREE,collection=false,title=''){
function coverArtwork(){
 const c=document.createElement('canvas');c.width=640;c.height=900;
 const x=c.getContext('2d');
 const bg=x.createLinearGradient(0,0,640,900);bg.addColorStop(0,'#1a427a');bg.addColorStop(.46,'#0b2556');bg.addColorStop(1,'#061631');
 x.fillStyle=bg;x.fillRect(0,0,640,900);
 // Starfield; deterministic so redraws do not change the pattern.
 for(let i=0;i<105;i++){const a=(i*139.9)%604+18,b=(i*279.13)%860+20,r=i%7===0?2.4:.85;x.beginPath();x.fillStyle=i%3===0?'rgba(255,223,142,.55)':'rgba(173,221,244,.35)';x.arc(a,b,r,0,Math.PI*2);x.fill()}
 x.strokeStyle='#e9bb67';x.lineWidth=11;x.strokeRect(24,25,592,850);x.lineWidth=3;x.strokeStyle='#fff0b0';x.strokeRect(39,40,562,820);x.strokeStyle='#a67737';x.strokeRect(56,57,528,786);
 const ornament=(cx,cy,sx,sy)=>{x.save();x.translate(cx,cy);x.scale(sx,sy);x.strokeStyle='#edc06d';x.lineWidth=5;x.beginPath();x.moveTo(0,118);x.bezierCurveTo(30,70,72,49,118,45);x.bezierCurveTo(52,35,38,17,30,0);x.bezierCurveTo(20,34,10,42,0,50);x.stroke();x.beginPath();x.moveTo(15,79);x.bezierCurveTo(37,47,61,32,94,22);x.stroke();x.restore()};
 ornament(49,48,1,1);ornament(591,48,-1,1);ornament(49,852,1,-1);ornament(591,852,-1,-1);
 x.translate(320,455);
 x.strokeStyle='#dfa952';x.lineWidth=6;
 for(const r of [176,140,108]){x.beginPath();x.arc(0,0,r,0,Math.PI*2);x.stroke()}
 x.strokeStyle='#fff0ac';x.lineWidth=4;
 for(let i=0;i<12;i++){const a=i*Math.PI/6;x.beginPath();x.moveTo(Math.cos(a)*148,Math.sin(a)*148);x.lineTo(Math.cos(a)*185,Math.sin(a)*185);x.stroke()}
 const star=[];for(let i=0;i<16;i++){const a=i*Math.PI/8-Math.PI/2,rr=i%2?48:(i%4===0?170:114);star.push([Math.cos(a)*rr,Math.sin(a)*rr])}
 x.beginPath();star.forEach(([u,v],i)=>i?x.lineTo(u,v):x.moveTo(u,v));x.closePath();x.fillStyle='#e8b75e';x.fill();x.lineWidth=4;x.strokeStyle='#fff0b4';x.stroke();
 x.beginPath();x.arc(0,0,34,0,Math.PI*2);x.fillStyle='#12335f';x.fill();x.strokeStyle='#fff3c1';x.stroke();
 x.fillStyle='#e7c374';for(const p of [-240,240]){x.beginPath();x.moveTo(0,p-18);x.lineTo(13,p);x.lineTo(0,p+18);x.lineTo(-13,p);x.closePath();x.fill()}
 x.setTransform(1,0,0,1,0,0);
 if(title){x.textAlign='center';x.fillStyle='#fff0b0';x.font='bold 42px sans-serif';const chars=[...title];for(let i=0;i<Math.ceil(chars.length/10);i++)x.fillText(chars.slice(i*10,(i+1)*10).join(''),320,130+i*54,490);x.textAlign='left'}
 const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=8;return t;
}

const group=new THREE.Group(),binding=new THREE.Group();group.add(binding);
const left=new THREE.Group(),right=new THREE.Group();binding.add(left,right);
const mats=[];const material=(color,metalness=0)=>{const m=new THREE.MeshStandardMaterial({color,metalness,roughness:.6});mats.push(m);return m};
const leather=material(collection?'#294958':'#14366d',.2),gold=material('#d2ae63',.55),paper=material('#eedbb5'),dark=material('#0c1522'),panel=material('#293f56');
const targets=[],buttons=[],art=coverArtwork();
function box(parent,w,h,d,x,y,z,mat){const o=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),mat);o.position.set(x,y,z);parent.add(o);return o}
function textMap(lines,active){const c=document.createElement('canvas');c.width=640;c.height=410;const x=c.getContext('2d');x.fillStyle=active?'#102538':'#050a11';x.fillRect(0,0,640,410);if(active){x.fillStyle='#e4f7ff';x.font='bold 42px sans-serif';lines.forEach((line,i)=>x.fillText(line,30,85+i*70,575))}const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;return t}
let screen=null,screenKey='',screenCancel=()=>{};
function slot(side,x,y,index){box(side,.68,.91,.045,x,y,.16,gold);const well=box(side,.62,.85,.025,x,y,.19,dark);well.userData.atlasSlot=index;
 const node=new THREE.Object3D();node.position.set(x,y,.26);side.add(node);targets.push({node,index,well});}
for(const [side,sign] of [[left,-1],[right,1]]){
 box(side,2.66,3.64,.17,sign*1.345,0,-.23,leather);box(side,2.5,3.5,.23,sign*1.31,0,-.02,paper);box(side,2.44,3.44,.03,sign*1.31,0,.12,sign===1&&!collection?panel:paper);
 const outer=new THREE.Mesh(new THREE.PlaneGeometry(2.51,3.5),new THREE.MeshBasicMaterial({map:art,side:THREE.DoubleSide}));outer.position.set(sign*1.345,0,-.32);outer.rotation.y=Math.PI;side.add(outer);
 if(sign===-1||collection){for(let r=0;r<3;r++)for(let c=0;c<3;c++)slot(side,sign===-1?-2.05+c*.72:.61+c*.72,1.04-r*1.04,(sign===-1?0:9)+r*3+c)}
 else{
  box(side,2.13,1.73,.06,1.35,.66,.18,gold);
  screen=new THREE.Mesh(new THREE.PlaneGeometry(2.02,1.61),new THREE.MeshBasicMaterial({map:textMap([],false)}));screen.position.set(1.35,.66,.22);
  screen.userData.atlasScreen=true;side.add(screen);
  slot(side,.92,-1.13,9);
  // Genuine GBA labels and distribution: D-pad to the LEFT of A/B, SELECT/START above.
  // Keep the single physical card well (x=.92) and display entirely unobstructed.
  const specs=[
   ['UP',1.57,-.85,.145,'▲'],['DOWN',1.57,-1.46,.145,'▼'],
   ['LEFT',1.39,-1.15,.135,'◀'],['RIGHT',1.75,-1.15,.135,'▶'],
   ['A',2.35,-.99,.195,'A'],['B',2.12,-1.43,.18,'B'],
   ['SELECT',1.59,-.42,.13,'SEL'],['START',2.12,-.42,.13,'STA'],
   ['L',.53,1.63,.115,'L'],['R',2.18,1.63,.115,'R']
  ];
  for(const [control,x,y,radius,label] of specs){
   const color=control==='A'?'#d8b46f':control==='B'?'#8fc5b8':control==='START'||control==='SELECT'?'#65788c':'#303d53';
   const mat=material(color),b=new THREE.Mesh(new THREE.CylinderGeometry(radius,radius,.11,32),mat);
   b.rotation.x=Math.PI/2;b.position.set(x,y,.31);b.userData.atlasControl=control;side.add(b);buttons.push(b);
   const c=document.createElement('canvas');c.width=128;c.height=128;const ctx=c.getContext('2d');
   ctx.fillStyle=control==='A'||control==='B'?'#102231':'#ffffff';
   ctx.font='bold '+(label.length>1?39:70)+'px sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';
   ctx.fillText(label,64,66,120);
   const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;
   const mark=new THREE.Mesh(new THREE.PlaneGeometry(radius*1.88,radius*1.88),
    new THREE.MeshBasicMaterial({map:t,transparent:true,depthWrite:false,side:THREE.DoubleSide}));
   mark.position.set(x,y,.38);mark.userData.atlasControl=control;side.add(mark);
  }
 }
}
box(binding,.15,3.7,.35,0,0,-.1,leather);
return {group,targets,buttons,setOpen(amount){left.rotation.y=Math.PI*(1-amount);binding.position.x=-1.31*(1-amount)},
 screen(card,mode='card',interactive=false,upload={state:'idle',code:''},download={items:[],index:0,menuIndex:0,progress:null}){
  if(!screen)return;
  const menuKey=['menu','download','downloading','permanent','upgrading'].includes(mode)?
    (download.items||[]).map(c=>c.id+':'+c.revision).join(',')+':'+download.index+':'+download.menuIndex+
    ':'+(download.permanentIndex||0)+':'+(download.permanents||[]).map(c=>c.id+':'+c.revision).join(',')+
    ':'+(download.progress===null?'':Math.floor(download.progress/10)):'';
   const key=(card?card.id+':'+card.revision:'empty')+':'+mode+':'+(interactive?'GBA':'BOOK')+
    ':'+upload.state+':'+upload.code+':'+menuKey;
  if(key===screenKey)return;
  screenCancel();screenCancel=()=>{};screenKey=key;
  const map=screen.material.map,ctx=map.image.getContext('2d');
  const txt=(message,x,y,size=25)=>{
   ctx.fillStyle='#000000';ctx.font='bold '+size+'px sans-serif';
   const chars=Array.from(String(message||'')).slice(0,18).join('');
   ctx.fillText(chars,x,y,320);
  };
  const base=()=>{
   ctx.fillStyle='#e8e1ce';ctx.fillRect(0,0,640,410);
   ctx.fillStyle='#b59c68';ctx.fillRect(8,8,624,394);
   ctx.fillStyle='#f4ecda';ctx.fillRect(13,13,614,384);
   ctx.fillStyle='#000000';
  };
  if(!card){
   base();txt('萬物圖鑑',35,80,45);
   txt(mode==='menu'?'GBA 系統選單':'右側讀卡格尚未放卡',35,150,29);
   if(mode==='permanent'){
    const items=Array.isArray(download.permanents)?download.permanents:[];
    txt('BOOK 永久卡簿',35,85,34);
    txt('登錄：'+items.length+' 張｜本機',35,146,25);
    const chosen=items[Math.min(items.length-1,Math.max(0,download.permanentIndex||0))];
    txt(chosen?'Nº '+chosen.permanentNumber+' '+chosen.title:'暫無已認證卡片',35,215,27);
    txt(chosen?'A 調出原卡至 0 號':'插入已綁定原卡',35,300,22);
   }else if(mode==='menu'){
    const items=['卡片檢視','AI 觀察','卡片下載','永久卡管理','商店（未開放）'];
    for(let i=0;i<items.length;i++)
     txt((download.menuIndex===i?'▶ ':'  ')+items[i],35,202+i*40,23);
    txt('可先瀏覽範本，下載需空白卡',320,328,19);
   }else if(mode==='download'){
    // Users must be able to SEE the repeatable recipe even with no reader card.
    const items=Array.isArray(download.items)?download.items:[];
    const index=Math.min(items.length-1,Math.max(0,download.index||0));
    txt('卡片範本庫',35,165,32);
    txt(items.length?(index+1)+' / '+items.length:'沒有可用的範本',35,221,25);
    txt(items[index]?.title||'請建立可複製卡',35,266,28);
    txt('↑↓ 瀏覽 / 插入空白卡後按 A',35,318,21);
    txt('B 返回',35,368,21);
   }else txt('放入一張卡片即可顯示',35,200,23);
   map.needsUpdate=true;return;
  }
  const face=document.createElement('canvas');face.width=560;face.height=780;
  const redraw=()=>{
   if(screenKey!==key)return;
   base();ctx.drawImage(face,20,18,265,371);
   ctx.fillStyle='#b59c68';ctx.fillRect(295,18,317,371);
   ctx.fillStyle='#f4ecda';ctx.fillRect(300,23,307,361);
   txt('Nº '+(card.permanentRegistry?.state==='registered'?card.permanentNumber:(card.cardNumber||'—')),318,69,29);
   txt(card.title||'空白卡片',318,116,28);
   if(!interactive){
    txt('圖鑑雙頁模式',318,170,28);
    txt('點右側 GBA 進入',318,233,21);
    txt('近距離專注',318,283,26);
    txt('按鍵於下階段啟用',318,342,19);
   }else if(mode==='menu'){
     const options=['卡片檢視','AI 觀察','卡片下載','永久卡管理','商店（未開放）'];
     txt('GBA 系統選單',318,156,25);
     for(let i=0;i<options.length;i++)
      txt((download.menuIndex===i?'▶ ':'  ')+options[i],318,202+i*43,20);
   }else if(mode==='permanent'){
     const items=Array.isArray(download.permanents)?download.permanents:[];
     const i=Math.min(items.length-1,Math.max(0,download.permanentIndex||0));
     const selected=items[i];
     txt('BOOK 永久管理',318,157,24);
     if(card?.permanentRegistry?.state==='registered'){
      txt('Nº '+card.permanentNumber,318,207,27);
      txt('A 歸回專屬格',318,263,20);
      txt('B 返回',318,330,19);
     }else if(card){
      txt('一般契約卡',318,207,24);
      txt('A 申請永久認證',318,263,19);
      txt('不可逆｜本機試行',318,330,16);
     }else{
      txt(selected?.title||'暫無永久卡',318,219,22);
      txt('A 調出原卡',318,285,20);
     }
   }else if(mode==='upgrading'){
     const amount=Math.min(100,Math.max(0,download.progress||0));
     txt('永久契約進化',318,165,24);
     txt(String(Math.floor(amount))+' %',318,223,31);
     ctx.fillStyle='#c9af79';ctx.fillRect(318,259,270,24);
     ctx.fillStyle='#d9b659';ctx.fillRect(318,259,Math.round(2.7*amount),24);
     txt('確認後配發永久號',318,329,19);
   }else if(mode==='download'){
     txt('卡片範本庫',318,157,26);
     const items=Array.isArray(download.items)?download.items:[];
     if(!items.length){
      txt('沒有可用的卡片範本',318,225,21);
      txt('可建立普通卡片',318,268,19);
     }else{
      const i=Math.min(items.length-1,Math.max(0,download.index||0));
      txt((i+1)+' / '+items.length,318,199,22);
      txt(items[i]?.title||'未命名卡片',318,244,23);
      txt('↑↓ 選擇 / A 下載',318,292,19);
     }
     txt('B 返回',318,345,21);
   }else if(mode==='downloading'){
     const amount=Math.min(100,Math.max(0,download.progress||0));
     txt('下載卡片格式',318,168,24);
     txt(String(Math.floor(amount))+' %',318,221,32);
     ctx.fillStyle='#d4c4a1';ctx.fillRect(318,257,270,26);
     ctx.fillStyle='#508b7a';ctx.fillRect(318,257,Math.round(2.7*amount),26);
     txt('寫入空白卡中',318,330,21);
    }else if(mode==='observe'){
    txt('AI → GITHUB',318,174,26);
    if(upload.state==='sending'){
     txt('傳送中…',318,225,29);txt('請稍候',318,288,21);
    }else if(upload.state==='ok'){
     txt('✓ 上傳成功',318,225,29);
     txt('私人資料庫',318,281,21);
     txt((upload.code||'').slice(0,12),318,333,18);
    }else if(upload.state==='error'){
     txt('✕ 上傳失敗',318,225,29);
     txt((upload.code||'GITHUB_ERROR').slice(0,24),318,281,16);
     txt('A 重試',318,333,22);
    }else if(upload.state==='auth'){
     txt('待 GitHub 授權',318,225,23);
     txt('請在視窗確認',318,291,20);
    }else{
     txt('等待發送卡片',318,228,25);
     txt('SELECT 開啟選單',318,285,19);
     txt('A 發送私人 GitHub',318,334,20);
    }
   }else{
    txt(card.cardType||'未分類',318,172,24);
    txt(card.image?'圖片已載入':'尚未放入圖片',318,223,21);
    txt('SELECT 系統選單',318,278,19);
    txt('A 開啟卡片',318,326,23);
   }
   map.needsUpdate=true;root.LivingScene?.invalidate?.();
  };
  const preview=mode==='download'&&(download.items||[])[download.index]||card;
   screenCancel=root.LivingCompendiumCard.draw(face,preview,redraw)||(()=>{});
  redraw();
 },
 dispose(){screenCancel();const geo=new Set(),mat=new Set(),maps=new Set();group.traverse(o=>{if(o.geometry)geo.add(o.geometry);for(const m of (Array.isArray(o.material)?o.material:[o.material]))if(m){mat.add(m);if(m.map)maps.add(m.map)}});geo.forEach(g=>g.dispose());maps.forEach(t=>t.dispose());mat.forEach(m=>m.dispose())}};
},
cardTexture(THREE,record){
 const c=document.createElement('canvas');
 const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;
 const cancel=root.LivingCompendiumCard.draw(c,record,()=>{t.needsUpdate=true;root.LivingScene?.invalidate?.()});
 t.needsUpdate=true;t.addEventListener('dispose',cancel);return t;
}};
})(typeof self!=='undefined'?self:this);
