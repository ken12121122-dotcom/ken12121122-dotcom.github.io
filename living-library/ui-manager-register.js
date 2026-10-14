/* Living Library manager: public read-only catalog and GitHub refs. No token or private GEN2 access. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 else root.LivingLibraryManager=api;
})(typeof self!=='undefined'?self:this,function(){
'use strict';
const REPO='ken12121122-dotcom/ken12121122-dotcom.github.io';
const API='https://api.github.com/repos/'+REPO;
const BRANCH_PATTERN=/(?:card|book|library|memory-palace)/i;
const HISTORIC_PR_HINTS=Object.freeze({
 'public.gpt.skills':[208,210],
 'KW-GAME-MEMORY-PALACE-20261014':[199,200,201],
 'public.system.ui.map':[209]
});
function cardMembership(catalog){
 const members=new Map();
 for(const [book,ids]of Object.entries(catalog?.bookCards||{})){
  for(const id of ids){
   if(!members.has(id))members.set(id,[]);
   if(!members.get(id).includes(book))members.get(id).push(book);
  }
 }
 return members;
}
function catalogOverview(catalog){
 if(!catalog||!Array.isArray(catalog.books)||!catalog.cards||!catalog.bookCards)throw new Error('catalog source missing');
 const members=cardMembership(catalog);
 return {
  bookCount:catalog.books.length,
  cardCount:Object.keys(catalog.cards).length,
  placementCount:[...members.values()].reduce((n,b)=>n+b.length,0),
  sharedCards:[...members.values()].filter(b=>b.length>1).length,
  members
 };
}
function compareCatalogs(live,dev){
 const a=catalogOverview(live),b=catalogOverview(dev);
 const liveCards=Object.keys(live.cards),devCards=Object.keys(dev.cards);
 const la=new Set(liveCards),da=new Set(devCards);
 const onlyLive=liveCards.filter(x=>!da.has(x));
 const onlyDev=devCards.filter(x=>!la.has(x));
 const changed=[];
 for(const id of liveCards){
  if(!da.has(id))continue;
  const one=live.cards[id],two=dev.cards[id];
  if(['title','url','nativeRoute','kind'].some(k=>(one[k]||'')!==(two[k]||'')))changed.push(id);
 }
 const bookIds=[...new Set([...Object.keys(live.bookCards),...Object.keys(dev.bookCards)])];
 const placements=[];
 for(const book of bookIds){
  const x=live.bookCards[book]||[],y=dev.bookCards[book]||[];
  const sx=new Set(x),sy=new Set(y);
  const liveOnly=x.filter(id=>!sy.has(id)),devOnly=y.filter(id=>!sx.has(id));
  const differentOrder=liveOnly.length===0&&devOnly.length===0&&x.join('|')!==y.join('|');
  if(liveOnly.length||devOnly.length||differentOrder)
   placements.push({book,liveOnly,devOnly,differentOrder});
 }
 return {live:{books:a.bookCount,cards:a.cardCount,placements:a.placementCount,sharedCards:a.sharedCards},
  dev:{books:b.bookCount,cards:b.cardCount,placements:b.placementCount,sharedCards:b.sharedCards},
  liveOnlyCardIds:onlyLive,devOnlyCardIds:onlyDev,changedCardIds:changed,placements};
}
function listCards(catalog){
 const members=cardMembership(catalog);
 return Object.keys(catalog?.cards||{}).sort().map(id=>{
  const c=catalog.cards[id];
  return {id,title:c.title||id,number:c.number||'',url:c.url||'',nativeRoute:c.nativeRoute||null,
    kind:c.kind||'',books:members.get(id)||[],historicalPrHints:HISTORIC_PR_HINTS[id]||[]};
 });
}
async function readGithub(fetcher,path){
 if(typeof fetcher!=='function')throw new Error('network fetch unavailable');
 const response=await fetcher(API+path,{method:'GET',headers:{Accept:'application/vnd.github+json'},cache:'no-store'});
 if(!response||!response.ok)throw new Error('GitHub API unavailable (HTTP '+(response?.status||'unknown')+')');
 return response.json();
}
async function paged(fetcher,path,maxPages=10){
 const all=[];
 for(let page=1;page<=maxPages;page++){
  const join=path.includes('?')?'&':'?';
  const rows=await readGithub(fetcher,path+join+'per_page=100&page='+page);
  if(!Array.isArray(rows))throw new Error('GitHub response is not an array');
  all.push(...rows);
  if(rows.length<100)return all;
 }
 throw new Error('GitHub exceeds '+(100*maxPages)+' items: count not verified');
}
async function fetchGithubReport(fetcher){
 // Explicit, user-triggered request only. Does not poll or mutate GitHub.
 const [head,branches,openPrs]=await Promise.all([
  readGithub(fetcher,'/commits/main'),
  paged(fetcher,'/branches'),
  paged(fetcher,'/pulls?state=open')
 ]);
 if(!/^[a-f0-9]{40}$/i.test(head?.sha||''))throw new Error('GitHub main SHA missing');
 const names=branches.map(b=>b.name).filter(s=>typeof s==='string');
 if(names.length!==branches.length)throw new Error('GitHub branch list incomplete');
 const related=names.filter(name=>BRANCH_PATTERN.test(name));
 const prs=openPrs.filter(pr=>Number.isInteger(pr.number)&&typeof pr.head?.ref==='string')
  .map(pr=>({number:pr.number,title:pr.title||'',draft:!!pr.draft,
    head:pr.head.ref,headSha:pr.head.sha||'',base:pr.base?.ref||'',
    state:pr.state||'open',url:'https://github.com/'+REPO+'/pull/'+pr.number}));
 return {repo:REPO,mainSha:head.sha,branchCount:names.length,relatedBranchCount:related.length,
  relatedBranches:related,openPrCount:prs.length,openPrs:prs,queriedAt:new Date().toISOString()};
}
return Object.freeze({REPO,BRANCH_PATTERN,HISTORIC_PR_HINTS,catalogOverview,compareCatalogs,listCards,fetchGithubReport});
});
