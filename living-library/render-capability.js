/* Software GPUs can expose WebGL2 while stalling an Android WebView. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.LivingRenderCapability=api;})(typeof self!=='undefined'?self:this,function(){
 'use strict';
 function isSoftwareRenderer(name){return /swiftshader|llvmpipe|softpipe|software rasterizer|microsoft basic render/i.test(String(name||''));}
 return {isSoftwareRenderer};
});
