import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const C=createRequire(import.meta.url)('../living-library/render-capability.js');
test('software GPU fallback preserves hardware GPUs and unknown renderer privacy settings',()=>{
 for(const name of ['ANGLE (Google, Vulkan SwiftShader Device)','llvmpipe (LLVM)','Microsoft Basic Render Driver','softpipe'])assert.equal(C.isSoftwareRenderer(name),true,name);
 for(const name of ['Mali-G68','ANGLE (Qualcomm Adreno)','Apple GPU','Intel Iris Xe','',null])assert.equal(C.isSoftwareRenderer(name),false,String(name));
});
