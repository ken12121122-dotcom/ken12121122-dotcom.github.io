'use strict';
/**
 * Branch lifecycle policy v1.1: all branches in the frozen 2026-10-20
 * snapshot are explicitly exempt. Only NEW_TEMP PRs need the metadata gate.
 * This is a read-only PR check; it does not delete, merge, or change refs.
 */
const fs = require('node:fs');
const path = require('node:path');
const legacyFile = path.resolve(__dirname,'../../docs/governance/branch-lifecycle-legacy-snapshot-20261020.json');
const expectedRepo = 'ken12121122-dotcom/ken12121122-dotcom.github.io';
const required = ['Task-ID','Change-Class','Base-SHA','Reuse-Check','Scope','Preserve','Tests','Rollback','Closeout'];
const validPrefix = /^(feat|feature|fix|docs|policy|candidate|work|chore|hotfix|ci|refactor)\/[a-zA-Z0-9._-]+$/;
const shaPattern = /^[0-9a-f]{40}$/i;
function extract(body,key) {
 const escaped=key.replace(/[.*+?^$\{\}()|[\]\\]/g,'\\$&');
 const pattern=new RegExp('^\\s*'+escaped+'\\s*:\\s*(.*?)\\s*$','im');
 return pattern.exec(body||'')?.[1]?.trim()||'';
}
function evaluate(pr, legacy, options={}) {
 const branch=pr?.head?.ref, headRepo=pr?.head?.repo?.full_name;
 if(!branch||!headRepo) return {status:'FAIL',class:'INVALID',errors:['missing GitHub pull_request head.ref/repo']};
 const old=new Set(legacy?.branches||[]);
 if(headRepo===expectedRepo && old.has(branch)) {
  return {status:'SKIP',class:'LEGACY',branch,errors:[],note:'Pre-existing branch is exempt; unchanged by v1.1'};
 }
 const body=pr.body||'',errors=[];
 if(!validPrefix.test(branch)) errors.push('new branch needs allowed task-scoped prefix');
 const v={};for(const field of required){v[field]=extract(body,field);if(!v[field])errors.push('missing PR metadata: '+field);}
 if(v['Change-Class']&&!/^(C1|C2|C3)$/.test(v['Change-Class']))errors.push('Change-Class must be C1, C2 or C3 (C0 must not create a branch)');
 if(v['Base-SHA']&&!shaPattern.test(v['Base-SHA']))errors.push('Base-SHA must be a 40-character commit SHA');
 if(v['Closeout']&&v['Closeout']!=='DELETE_AFTER_MERGE_OR_REPORT_BLOCKER')errors.push('Closeout must be DELETE_AFTER_MERGE_OR_REPORT_BLOCKER');
 for(const k of ['Task-ID','Reuse-Check','Scope','Preserve','Tests','Rollback']){
  if(v[k]&&/^(n\/a|tbd|todo|pending|\.{1,3}|-|待補|待定)$/i.test(v[k]))errors.push(k+' contains a placeholder, not evidence');
 }
 if(v['Reuse-Check']&&v['Reuse-Check'].length<8)errors.push('Reuse-Check needs actual reference / inspection result');
 if(v['Tests']&&v['Tests'].length<8)errors.push('Tests must state PASS, FAIL, or NOT_TESTED and scope');
 if(v['Tests']&&!/\b(PASS|FAIL|NOT_TESTED)\b/i.test(v['Tests']))errors.push('Tests must have an explicit evidence status');
 return {status:errors.length?'FAIL':'PASS',class:'NEW_TEMP',branch,errors,metadata:v};
}
if(require.main===module){
 try{
  const p=process.env.GITHUB_EVENT_PATH;
  if(!p)throw Error('GITHUB_EVENT_PATH is required');
  const event=JSON.parse(fs.readFileSync(p,'utf8'));
  const legacy=JSON.parse(fs.readFileSync(legacyFile,'utf8'));
  const result=evaluate(event.pull_request,legacy);
  console.log(JSON.stringify({status:result.status,class:result.class,branch:result.branch,errors:result.errors,note:result.note||''},null,2));
  if(result.status==='FAIL')process.exitCode=1;
 }catch(err){console.error('Branch lifecycle gate failed:',err.message);process.exitCode=1;}
}
module.exports={evaluate,extract,expectedRepo};
