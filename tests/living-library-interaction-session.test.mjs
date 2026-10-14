import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const {TargetInteractionSession}=createRequire(import.meta.url)('../living-library/live/interaction-session.js');
const make=()=>{let time=100;return {session:new TargetInteractionSession({now:()=>time,idFactory:()=> 'synthetic-run',maxAgeMs:500}),setTime:value=>time=value}};
const grab=(session,id='book.synthetic.a',pointer_id=7)=>session.beginGrab({target_id:id,pointer_id});
const match=(session,token,transcript='synthetic phrase')=>session.resolveVoiceResult(token,{transcript,incantations:['synthetic phrase','synthetic alternate']});
test('matching result retains canonical target and gesture identity, without transcript or runtime success claim',()=>{
 const {session}=make(),target=grab(session),token=session.captureVoiceContext(),result=match(session,token);
 assert.equal(result.matched,true);assert.equal(result.context.target_object_instance_id,target.target_object_instance_id);assert.equal(result.context.gesture_session_id,target.gesture_session_id);assert.equal(result.context.voice_session_id,token.voice_session_id);assert.equal(result.runtime_verified,false);assert.ok(!JSON.stringify(result).includes('synthetic phrase'));assert.ok(Object.isFrozen(result.context));
});
test('released and cancelled grabs cannot accept delayed results',()=>{
 for(const cancel of [s=>s.endGrab(7),s=>s.cancel()]){const {session}=make();grab(session);const token=session.captureVoiceContext();cancel(session);assert.equal(match(session,token).reason,'STALE_INTERACTION');assert.equal(session.current(),null)}
});
test('replacement target and regrabbing the same book both invalidate old sessions',()=>{
 for(const id of ['book.synthetic.b','book.synthetic.a']){const {session}=make();const before=grab(session),token=session.captureVoiceContext();const after=grab(session,id);assert.notEqual(before.gesture_session_id,after.gesture_session_id);assert.equal(match(session,token).reason,'STALE_INTERACTION');assert.equal(match(session,session.captureVoiceContext()).context.target_object_instance_id,id)}
});
test('another pointer release preserves the active grab, and wrong target types fail before mutation',()=>{
 const {session}=make();const target=grab(session),token=session.captureVoiceContext();assert.equal(session.endGrab(8),false);assert.throws(()=>session.beginGrab({target_id:null,pointer_id:8}),e=>e.code==='INVALID_TARGET');assert.equal(session.current(),target);assert.equal(match(session,token).matched,true);
});
test('expired, backwards or invalid clocks reject effects and do not leak timing input',()=>{
 for(const [time,reason] of [[601,'EXPIRED_SESSION'],[99,'EXPIRED_SESSION'],[NaN,'INVALID_CLOCK']]){const {session,setTime}=make();grab(session);const token=session.captureVoiceContext();setTime(time);const r=match(session,token);assert.equal(r.matched,false);assert.equal(r.reason,reason);assert.equal(r.context,null)}
});
test('replay, superseded voice result, and copied or forged tokens are rejected',()=>{
 const {session}=make();grab(session);const old=session.captureVoiceContext(),latest=session.captureVoiceContext();assert.equal(match(session,old).reason,'STALE_INTERACTION');assert.equal(match(session,{...latest}).reason,'INVALID_SESSION');assert.equal(match(session,Object.freeze({voice_session_id:latest.voice_session_id})).reason,'INVALID_SESSION');assert.equal(match(session,latest).matched,true);assert.equal(match(session,latest).reason,'ALREADY_CONSUMED');
});
test('wrong or invalid phrases cannot match; alternate phrase can match only with a fresh token',()=>{
 const {session}=make();grab(session);const token=session.captureVoiceContext();assert.equal(match(session,token,'wrong').reason,'NOT_MATCHED');assert.equal(match(session,token).reason,'ALREADY_CONSUMED');assert.equal(match(session,session.captureVoiceContext(),'synthetic alternate').matched,true);assert.equal(session.resolveVoiceResult(session.captureVoiceContext(),{transcript:'x',incantations:[' ']}).reason,'INVALID_INCANTATION');
});
test('session metadata is transient and immutable; no active target cannot capture a voice context',()=>{
 const {session}=make();assert.throws(()=>session.captureVoiceContext(),e=>e.code==='NO_ACTIVE_TARGET');const target=grab(session);assert.throws(()=>{target.target_object_instance_id='other'},TypeError);const token=session.captureVoiceContext();assert.throws(()=>{token.voice_session_id='forged'},TypeError);session.cancel();assert.equal(session.current(),null);assert.equal(match(session,null).reason,'INVALID_SESSION');
});
test('a matched context must still be current at effect time; copies and later invalidation fail',()=>{
 for(const invalidate of [(s,setTime)=>s.cancel(),(s,setTime)=>grab(s,'book.synthetic.b'),(s,setTime)=>s.captureVoiceContext(),(s,setTime)=>setTime(601)]){
  const {session,setTime}=make();grab(session);const context=match(session,session.captureVoiceContext()).context;
  assert.equal(session.isCurrent(context),true);assert.equal(session.isCurrent({...context}),false);invalidate(session,setTime);assert.equal(session.isCurrent(context),false);
 }
});
