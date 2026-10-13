/* Candidate-only GitHub transport; all GEN2 parsing stays in PackageCanvas. */
(function(root,factory){
 const G=typeof module==='object'&&module.exports?require('../packagecanvas/gen2-source.js'):root.PackageCanvasGen2;
 const api=factory(G);if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingLibraryDrafts=api;
})(typeof self!=='undefined'?self:this,function(G){
 'use strict';
 const REPO='ken12121122-dotcom/gen2-knowledge',API='https://api.github.com/repos/'+REPO;
 function candidateText(text){
  const parsed=G.parseFrontmatter(text);if(!parsed.data)throw new Error('缺少 frontmatter');
  const match=/^﻿?---[^\n]*\n([\s\S]*?)\r?\n---/.exec(text);
  let header=match[1].replace(/^status:.*$/m,'status: draft');
  if(!/^status:/m.test(header))header+='\nstatus: draft';
  header=header.replace(/^review_status:.*$/m,'review_status: generated');
  if(!/^review_status:/m.test(header))header+='\nreview_status: generated';
  return '---\n'+header+'\n---\n'+parsed.body.replace(/^\r?\n/,'');
 }
 function prepareDraft(source,path,text){
  const projection=G.buildLibraryProjection(source);
  const doc=projection.documents.find(d=>d.path===path);
  if(!doc?.editable)throw new Error('此文件唯讀；治理規則與 Resource 採用不由一般編輯器修改');
  const next=G.parseFrontmatter(text),old=G.parseFrontmatter(doc.text);
  if(!next.data)throw new Error('缺少 frontmatter');
  for(const key of ['document_type','kb_id','skill_id','workflow_id','output_id'])
   if((old.data[key]||'')!==(next.data[key]||''))throw new Error('不能改寫身份欄位：'+key);
  if(next.data.status!=='draft'||next.data.review_status!=='generated')throw new Error('候選內容必須是 draft / generated，不可自行核准');
  if(text===doc.text)throw new Error('內容未變更');
  if(new TextEncoder().encode(text).length>2*1024*1024)throw new Error('文件過大');
  const changed={...source,files:source.files.map(f=>f.path===path?{...f,text}:f)};
  const baseline=G.buildProject(source).report,after=G.buildProject(changed).report;
  const signature=i=>i.code+'|'+i.message;
  const known=new Set(baseline.issues.filter(i=>i.severity==='error').map(signature));
  const errors=after.issues.filter(i=>i.severity==='error'&&!known.has(signature(i)));
  if(errors.length)throw new Error('GEN2 檢查新增錯誤：'+errors.map(i=>i.message).join('；'));
  return {path,text,baseCommit:source.commit,baseText:doc.text,formalId:doc.formalId,
   warnings:after.issues.filter(i=>i.severity!=='error').map(i=>i.message),
   baselineErrors:baseline.issues.filter(i=>i.severity==='error').length,review_status:'generated'};
 }
 async function submitDraft(draft,token,options={}){
  if(!token)throw new Error('提交 PR 需要本次專用寫入權杖');
  if(!/^02_KNOWLEDGE_BASES\/KB-[A-Za-z0-9]+_[^/]+\/(?:KB_CONTRACT\.md|(?:03_WORKFLOW_BOM|04_WORKFLOWS|05_SKILLS|06_OUTPUTS)\/[^/]+\.md)$/.test(draft.path||''))throw new Error('編輯路徑不在受控 KB');
  if(!/^[0-9a-f]{40}$/.test(draft.baseCommit||'')||draft.review_status!=='generated')throw new Error('無效的候選版本');
  const fm=G.parseFrontmatter(draft.text).data;if(fm?.status!=='draft'||fm?.review_status!=='generated')throw new Error('未通過候選状态檢查');
  const branch=options.branch||'candidate/living-library-'+Date.now()+'-'+crypto.randomUUID().slice(0,8);
  if(!/^candidate\/living-library-[A-Za-z0-9-]+$/.test(branch))throw new Error('僅允許暫時候選分支');
  let phase='read_main',branchCreated=false;
  const request=async(path,method='GET',body)=>{
   if(options.signal?.aborted)throw new Error('提交已中止');
   const r=await (options.fetchImpl||fetch)(API+path,{method,cache:'no-store',signal:options.signal,
    headers:{Accept:'application/vnd.github+json',Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},
    ...(body?{body:JSON.stringify(body)}:{})});
   if(!r.ok)throw new Error('GitHub '+phase+' 失敗（HTTP '+r.status+'）；請確認 Contents 與 Pull requests 寫入權限');
   return r.json();
  };
  try{
   const main=await request('/commits/main');if(main.sha!==draft.baseCommit)throw new Error('main 已更新，請重新讀取後再編輯；未寫入任何候選');
   const file=await request('/contents/kb/'+draft.path.split('/').map(encodeURIComponent).join('/')+'?ref='+draft.baseCommit);
   if(file.type!=='file'||file.encoding!=='base64')throw new Error('來源文件無法完整驗證');
   const original=new TextDecoder().decode(Uint8Array.from(atob(file.content.replace(/\s/g,'')),c=>c.charCodeAt(0)));
   if(original!==draft.baseText)throw new Error('來源內容不一致，停止提交');
   phase='create_blob';const blob=await request('/git/blobs','POST',{content:draft.text,encoding:'utf-8'});
   phase='create_tree';const tree=await request('/git/trees','POST',{base_tree:main.commit.tree.sha,tree:[{path:'kb/'+draft.path,mode:'100644',type:'blob',sha:blob.sha}]});
   phase='create_commit';const commit=await request('/git/commits','POST',{message:'Candidate MD edit: '+(draft.formalId||draft.path),tree:tree.sha,parents:[draft.baseCommit]});
   phase='create_candidate_branch';await request('/git/refs','POST',{ref:'refs/heads/'+branch,sha:commit.sha});branchCreated=true;
   phase='create_draft_pr';const pr=await request('/pulls','POST',{base:'main',head:branch,draft:true,title:'Candidate MD edit: '+(draft.formalId||draft.path),body:'此 PR 是 Living Library 編輯器產生的完整 MD 候選。\n\n來源 main commit: '+draft.baseCommit+'\n文件: kb/'+draft.path+'\nreview_status: generated\n\n本機 PackageCanvas 解析器檢查：未新增 error-level issue；既有錯誤數 '+draft.baselineErrors+'。仍須等待 GEN2 Check 與 OWNER 審核。只有 OWNER 合併才成為正式版本。'});
   if(!/^https:\/\/github\.com\/ken12121122-dotcom\/gen2-knowledge\/pull\/\d+$/.test(pr.html_url||''))throw new Error('PR 回傳網址不符合正式 repo');
   return {url:pr.html_url,branch,commit:commit.sha,review_status:'generated',gen2_check:'pending'};
  }catch(e){if(branchCreated)e.recoveryUrl='https://github.com/'+REPO+'/compare/main...'+branch+'?expand=1';e.phase=phase;throw e;}
 }
 return {candidateText,prepareDraft,submitDraft};
});
