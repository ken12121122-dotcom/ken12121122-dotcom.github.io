/* One extensible command dispatcher; enabled by a character gesture, not on load. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingAtlasVoice=api;})(typeof self!=='undefined'?self:this,function(){
'use strict';
function create({host,commands,onState=()=>{},now=()=>Date.now()}){
 const Recognition=host.SpeechRecognition||host.webkitSpeechRecognition;
 let wanted=false,recognition=null,starting=false,timer=null,generation=0,last=-Infinity,failures=0,nativeSession=null;
 const native=host.LivingLibraryNativeVoice;
 function state(status,detail=''){onState({status,detail,wanted})}
 function execute(command,confirmed=false){if(!wanted||(!confirmed&&now()-last<220))return false;last=now();commands[command]();return true}
 function dispatch(text){if(!wanted)return;const word=String(text).trim().toLowerCase();
  const command=Object.keys(commands).find(k=>new RegExp('(^|[^a-z])'+k+'([^a-z]|$)','i').test(word));
  if(!command)return false;return execute(command);
 }
 function stop(){if(nativeSession){try{native.postMessage(JSON.stringify({v:1,op:'stop',session:nativeSession}))}catch{}nativeSession=null}wanted=false;generation++;starting=false;if(timer!==null)host.clearTimeout(timer);timer=null;
  if(recognition){recognition.onend=null;recognition.onresult=null;recognition.onerror=null;try{recognition.abort()}catch{}}recognition=null;state('off');}
 function start(){if(wanted)return;wanted=true;const run=++generation;
  if(native&&typeof native.postMessage==='function'){
   nativeSession='voice-'+now()+'-'+generation;
   native.onmessage=e=>{let d;try{d=JSON.parse(e.data)}catch{return}if(d.v!==1||d.session!==nativeSession||run!==generation)return;
    if(d.status==='result'){if(wanted)dispatch(d.text)}else if(['starting','listening','off','error'].includes(d.status)){
     if(d.status==='off'||d.status==='error')wanted=false;state(d.status,d.detail||'');
    }
   };
   state('starting');try{native.postMessage(JSON.stringify({v:1,op:'start',session:nativeSession}))}catch(e){wanted=false;state('error',e.message)}return;
  }
  if(!Recognition){wanted=false;state('unsupported','此環境沒有語音辨識接口；APP 語音連接待測');return}
  recognition=new Recognition();recognition.lang='en-US';recognition.continuous=true;recognition.interimResults=true;
  const current=recognition,seen=new Map();
  function begin(){if(!wanted||run!==generation||starting)return;starting=true;try{current.start()}catch(e){starting=false;wanted=false;state('error',e.message)}}
  current.onstart=()=>{if(run!==generation)return;starting=false;failures=0;seen.clear();state('listening')};
  current.onresult=e=>{if(run!==generation||!wanted)return;for(let i=e.resultIndex;i<e.results.length;i++){
   const text=String(e.results[i][0].transcript),previous=seen.get(i)||{};
   for(const command of Object.keys(commands)){
    const count=(text.match(new RegExp('(^|[^a-z])'+command+'(?=[^a-z]|$)','gi'))||[]).length;
    const old=previous[command]||0;for(let n=old;n<count;n++)execute(command,true);
    previous[command]=Math.max(old,count);
   }
   seen.set(i,previous);
  }};
  current.onerror=e=>{if(run!==generation)return;starting=false;
   if(['not-allowed','service-not-allowed','audio-capture','network'].includes(e.error)||++failures>=3){wanted=false;state('error',e.error)}
  };
  current.onend=()=>{if(run!==generation||!wanted)return;starting=false;timer=host.setTimeout(()=>{timer=null;begin()},600)};
  state('starting');begin();
 }
 return {start,stop,dispatch,get wanted(){return wanted}};
}
return {create};
});
