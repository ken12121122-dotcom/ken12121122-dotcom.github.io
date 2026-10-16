/* Shared flat card face. No object creation, IDs, automatic numbering or executable content. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingCompendiumCard=api;})(typeof self!=='undefined'?self:globalThis,function(){
 'use strict';
 const paints=new WeakMap();
 function draw(canvas,record={},invalidate=()=>{}){
  const ticket={cancelled:false};const old=paints.get(canvas);if(old)old.cancelled=true;paints.set(canvas,ticket);
  canvas.width=560;canvas.height=780;const x=canvas.getContext('2d');
  x.fillStyle='#35494c';x.fillRect(0,0,560,780);x.fillStyle='#f3ead2';x.fillRect(15,15,530,750);
  x.strokeStyle='#ac8750';x.lineWidth=2;x.strokeRect(23,23,514,734);
  const text=(value,size,px,py,width=466)=>{x.fillStyle='#000000';x.font=size+'px sans-serif';x.fillText(String(value),px,py,width)};
  text('Nº '+(record.cardNumber||'—'),29,46,66,170);text('圖鑑卡',23,411,66,103);
  x.strokeStyle='#35494c';x.beginPath();x.moveTo(44,82);x.lineTo(516,82);x.stroke();
  text(record.title||'未命名',38,44,130);
  x.fillStyle='#e6dcc1';x.fillRect(44,154,472,376);x.strokeRect(44,154,472,376);
  text('尚未放入圖片',25,179,350,250);
  text(record.cardType||'未分類',23,44,571,290);text(record.linkedCardId?'已連結':'未連結',21,426,571,90);
  x.strokeStyle='#b3a78a';x.beginPath();x.moveTo(44,589);x.lineTo(516,589);x.stroke();
  const chars=[...(record.description||'')];for(let i=0;i<4;i++)text(chars.slice(i*20,(i+1)*20).join(''),23,44,626+i*30);
  text('念動世界',19,44,743,220);
  const picture=record.image;
  if(picture&&typeof picture.dataUrl==='string'&&/^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(picture.dataUrl)&&picture.dataUrl.length<=70000){
   const image=new Image();image.onload=()=>{
    if(ticket.cancelled||!image.naturalWidth||!image.naturalHeight)return;
    const zoom=Number.isFinite(picture.scale)?Math.min(3,Math.max(1,picture.scale)):1;
    const pos=n=>Number.isFinite(n)?Math.min(100,Math.max(0,n))/100:.5;
    const size=Math.max(472/image.naturalWidth,376/image.naturalHeight)*zoom,w=image.naturalWidth*size,h=image.naturalHeight*size;
    x.save();x.beginPath();x.rect(45,155,470,374);x.clip();x.drawImage(image,44+(472-w)*pos(picture.x),154+(376-h)*pos(picture.y),w,h);x.restore();invalidate();
   };image.src=picture.dataUrl;
  }
  return ()=>{ticket.cancelled=true};
 }
 return Object.freeze({draw});
});
