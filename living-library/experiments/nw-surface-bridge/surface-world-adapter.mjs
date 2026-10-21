/**
 * NW-SURFACE-BRIDGE-001 | Read-only Surface Lab 005 (v4 JSON) -> world geometry.
 * This adapter never touches the existing Living Library runtime, contracts, cards, or storage.
 * Surface Lab dimensions are centimeters; the preview world uses meters and Y-up.
 * No collision/physics/edit write-back is implied by this geometry adapter.
 */
const vec=(p)=>[p.x,p.y,p.z];
const add=(a,b)=>a.map((n,i)=>n+b[i]);
const sub=(a,b)=>a.map((n,i)=>n-b[i]);
const mul=(a,k)=>a.map(n=>n*k);
const dot=(a,b)=>a.reduce((t,n,i)=>t+n*b[i],0);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const mag=(a)=>Math.hypot(...a);
const unit=(a)=>mag(a)>1e-9?mul(a,1/mag(a)):null;
const xyz=(a)=>({x:a[0],y:a[1],z:a[2]});
const finite3=(p)=>p&&['x','y','z'].every(k=>Number.isFinite(p[k]));
const finite2=(p)=>p&&Number.isFinite(p.u)&&Number.isFinite(p.v);
const cm=(v)=>v*.01;
const framePoint=(f,p,h=0)=>add(add(add(f.a,mul(f.u,p.u)),mul(f.v,p.v)),mul(f.n,h));
const surfaceFrame=(s)=>{
  if(!finite3(s.a)||!finite3(s.b)||!finite3(s.c))return null;
  const a=vec(s.a),u=unit(sub(vec(s.b),a));
  if(!u)return null;
  const n=unit(cross(u,sub(vec(s.c),a)));
  if(!n)return null;
  const v=unit(cross(n,u));
  return {a,u,v,n};
};
const okPath=(s)=>Array.isArray(s.points)&&s.points.length>=3&&s.points.every(finite2)
  &&Number.isFinite(s.thickness)&&Math.abs(s.thickness)>=.5;
const area=(pts)=>pts.reduce((sum,p,i)=>{const q=pts[(i+1)%pts.length];return sum+p.u*q.v-q.u*p.v},0)/2;
const panelFrame=(parent,f,key)=>{
  const front=parent.points.map(p=>framePoint(f,p));
  const back=parent.points.map(p=>framePoint(f,p,parent.thickness));
  if(key==='front'){
    if(parent.kind==='base')return {a:f.a,u:f.u,v:mul(f.v,-1),n:mul(f.n,-1)};
    return f;
  }
  if(key==='end')return {...f,a:add(f.a,mul(f.n,parent.thickness))};
  const match=/^side:(\d+)$/.exec(key||'');
  if(!match)return null;
  const i=Number(match[1]);
  if(i>=front.length)return null;
  const j=(i+1)%front.length, a=front[i],u=unit(sub(front[j],a));
  if(!u)return null;
  const n=unit(cross(u,sub(back[i],a)));
  if(!n)return null;
  return {a,u,n,v:unit(cross(n,u))};
};
export function convertSurfaceLabWorld(input){
  if(!input||input.version!==4||input.unit!=='cm'||!Array.isArray(input.surfaces)||!Array.isArray(input.anchors)){
    throw new Error('期待 Surface Lab 005 的 v4 JSON（version=4, unit=cm, surfaces, anchors）');
  }
  const surfaces=new Map(),gaps=[],objects=[],frames=new Map(),resolving=new Set();
  for(const s of input.surfaces){
    if(!s||typeof s.id!=='string'||!s.id.trim()||surfaces.has(s.id)){
      gaps.push({surfaceId:s?.id||null,reason:'INVALID_OR_DUPLICATE_ID'});continue;
    }
    surfaces.set(s.id,s);
  }
  function resolve(s){
    if(frames.has(s.id))return frames.get(s.id);
    if(resolving.has(s.id))return null;
    resolving.add(s.id);
    let f=null;
    if(!s.attachment)f=surfaceFrame(s);
    else{
      const a=s.attachment,parent=surfaces.get(a.parentId);
      if(parent&&!parent.fold?.active&&okPath(parent)&&
        (a.vertexCount===undefined||a.vertexCount===parent.points.length)&&
        (a.foldCount===undefined||!parent.fold?.active)){
        const pf=resolve(parent);
        if(pf)f=panelFrame(parent,pf,a.panelKey);
      }
    }
    resolving.delete(s.id);
    if(f)frames.set(s.id,f);
    return f;
  }
  for(const s of surfaces.values()){
    if(!okPath(s)){gaps.push({surfaceId:s.id,reason:'INVALID_GEOMETRY'});continue;}
    if(s.fold?.active){gaps.push({surfaceId:s.id,reason:'FOLD_GEOMETRY_PENDING'});continue;}
    if(Math.abs(area(s.points))<.125){gaps.push({surfaceId:s.id,reason:'DEGENERATE_POLYGON'});continue;}
    const f=resolve(s);
    if(!f){gaps.push({surfaceId:s.id,reason:s.attachment?'UNRESOLVED_ATTACHMENT':'INVALID_FRAME'});continue;}
    // Geometry-only stable references; never mint or claim a card/contract/object ownership ID.
    objects.push({
      sourceId:s.id,
      nodeId:'surface:'+s.id,
      name:typeof s.name==='string'?s.name:s.id,
      kind:s.kind||'custom',
      origin:xyz(mul(f.a,.01)),
      basis:{u:xyz(f.u),v:xyz(f.v),n:xyz(f.n)},
      polygon:s.points.map(p=>({x:cm(p.u),y:cm(p.v)})),
      depth:cm(s.thickness),
      collisionStatus:'NOT_BUILT',
      cardContractStatus:'NOT_APPLICABLE',
      readonly:true
    });
  }
  return {
    schema:'nw-surface-scene/v0.1',
    source:{product:'Surface Lab 005',version:input.version,unit:'cm',readOnly:true},
    world:{unit:'m',axes:'Y_UP',mode:'preview_only'},
    objects,gaps,
    anchorCount:input.anchors.length
  };
}
