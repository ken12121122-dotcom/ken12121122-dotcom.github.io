/* Three.js model derived from the user supplied v0.2 sandbox. No private data. */
(function(root){'use strict';
root.LivingAtlasModel={create(THREE,collection=false){
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
 const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=8;return t;
}

const group=new THREE.Group(),binding=new THREE.Group();group.add(binding);
const left=new THREE.Group(),right=new THREE.Group();binding.add(left,right);
const mats=[];const material=(color,metalness=0)=>{const m=new THREE.MeshStandardMaterial({color,metalness,roughness:.6});mats.push(m);return m};
const leather=material(collection?'#294958':'#14366d',.2),gold=material('#d2ae63',.55),paper=material('#eedbb5'),dark=material('#0c1522'),panel=material('#293f56');
const targets=[],buttons=[],art=coverArtwork();
function box(parent,w,h,d,x,y,z,mat){const o=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),mat);o.position.set(x,y,z);parent.add(o);return o}
function textMap(lines,active){const c=document.createElement('canvas');c.width=640;c.height=410;const x=c.getContext('2d');x.fillStyle=active?'#102538':'#050a11';x.fillRect(0,0,640,410);if(active){x.fillStyle='#e4f7ff';x.font='bold 42px sans-serif';lines.forEach((line,i)=>x.fillText(line,30,85+i*70,575))}const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;return t}
let screen=null;
function slot(side,x,y,index){box(side,.68,.91,.045,x,y,.16,gold);const well=box(side,.62,.85,.025,x,y,.19,dark);well.userData.atlasSlot=index;
 const node=new THREE.Object3D();node.position.set(x,y,.26);side.add(node);targets.push({node,index,well});}
for(const [side,sign] of [[left,-1],[right,1]]){
 box(side,2.66,3.64,.17,sign*1.345,0,-.23,leather);box(side,2.5,3.5,.23,sign*1.31,0,-.02,paper);box(side,2.44,3.44,.03,sign*1.31,0,.12,sign===1&&!collection?panel:paper);
 const outer=new THREE.Mesh(new THREE.PlaneGeometry(2.51,3.5),new THREE.MeshBasicMaterial({map:art,side:THREE.DoubleSide}));outer.position.set(sign*1.345,0,-.32);outer.rotation.y=Math.PI;side.add(outer);
 if(sign===-1||collection){for(let r=0;r<3;r++)for(let c=0;c<3;c++)slot(side,sign===-1?-2.05+c*.72:.61+c*.72,1.04-r*1.04,(sign===-1?0:9)+r*3+c)}
 else{
  box(side,2.13,1.73,.06,1.35,.66,.18,gold);
  screen=new THREE.Mesh(new THREE.PlaneGeometry(2.02,1.61),new THREE.MeshBasicMaterial({map:textMap([],false)}));screen.position.set(1.35,.66,.22);side.add(screen);
  slot(side,.92,-1.13,9);
  for(const [control,x,y] of [['up',2.07,-.92],['down',2.07,-1.36],['left',1.85,-1.14],['right',2.29,-1.14],['confirm',2.07,-1.14]]){
   const b=new THREE.Mesh(new THREE.CylinderGeometry(control==='confirm'?.13:.11,control==='confirm'?.13:.11,.08,24),gold);b.rotation.x=Math.PI/2;b.position.set(x,y,.23);b.userData.atlasControl=control;side.add(b);buttons.push(b);
  }
 }
}
box(binding,.15,3.7,.35,0,0,-.1,leather);
let screenKey='';
return {group,targets,buttons,setOpen(amount){left.rotation.y=-Math.PI*(1-amount);binding.position.x=-1.31*(1-amount)},
 screen(card){if(!screen)return;const key=card?card.id+':'+card.revision:'';if(key===screenKey)return;screenKey=key;screen.material.map.dispose();screen.material.map=textMap(card?[card.title,'已放入讀卡區','● 開啟內頁']:[],!!card)},
 dispose(){const geo=new Set(),mat=new Set(),maps=new Set();group.traverse(o=>{if(o.geometry)geo.add(o.geometry);for(const m of (Array.isArray(o.material)?o.material:[o.material]))if(m){mat.add(m);if(m.map)maps.add(m.map)}});geo.forEach(g=>g.dispose());maps.forEach(t=>t.dispose());mat.forEach(m=>m.dispose())}};
},
cardTexture(THREE,record){
 const c=document.createElement('canvas');c.width=560;c.height=780;const x=c.getContext('2d');
 x.fillStyle='#d8b776';x.fillRect(0,0,560,780);x.strokeStyle='#876331';x.lineWidth=13;x.strokeRect(16,16,528,748);
 x.fillStyle='#f8e9bc';x.fillRect(37,38,486,87);x.fillStyle='#46341c';x.font='bold 40px sans-serif';x.fillText(record.title||'空白卡片',53,93,450);
 x.fillStyle='#916a3c';x.fillRect(41,145,478,427);const neb=x.createRadialGradient(282,355,28,282,355,305);neb.addColorStop(0,'#377ba9');neb.addColorStop(1,'#09152e');x.fillStyle=neb;x.fillRect(54,158,452,400);
 x.strokeStyle='#d8bb73';x.lineWidth=7;for(const r of [50,114,160]){x.beginPath();x.arc(280,360,r,0,Math.PI*2);x.stroke()}
 x.fillStyle='#ffe6a2';x.font='130px serif';x.textAlign='center';x.fillText('✦',280,405);x.textAlign='left';
 x.strokeStyle='#fce6a3';x.strokeRect(56,160,448,396);x.fillStyle='#f5eddb';x.fillRect(38,588,484,149);x.fillStyle='#3c372c';x.font='25px sans-serif';
 const chars=[...(record.description||'空白卡片 · 點選可編輯')];for(let i=0;i<3;i++)x.fillText(chars.slice(i*17,(i+1)*17).join(''),55,628+i*38,450);
 const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;return t;
}};
})(typeof self!=='undefined'?self:this);
