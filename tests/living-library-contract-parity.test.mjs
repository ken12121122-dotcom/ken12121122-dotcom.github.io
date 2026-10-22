import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url),{cases,fixture}=require('./browser/contract-cases.cjs'),Contract=require('../living-library/live/spell-contract.js');
for(const entry of cases)test('contract parity: '+entry.name,()=>{
 const f=fixture(),card=entry.kind==='rune'?f.rune:f.card;entry.change(card);
 if(entry.semantic){const result=Contract[entry.kind==='rune'?'validateRune':'validateSpell'](card,f.registry);assert.ok(result);if(entry.kind==='spell')assert.equal(result.runtime_verified,false)}
 else assert.throws(()=>Contract[entry.kind==='rune'?'validateRune':'validateSpell'](card,f.registry),e=>typeof e.code==='string');
 assert.deepEqual(f.calls,[],'validation must never invoke guards or handlers');
});
