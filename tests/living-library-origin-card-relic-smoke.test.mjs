import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const R=require('../living-library/live/permanent-card-3d.js');
test('3D identity requires permanent registration not a visible number',()=>{
 assert.equal(R.registered({id:'id',cardNumber:'001'}),false);
 assert.equal(R.registered({id:'id',permanentRegistry:{state:'registered',cardId:'id'}}),true);
});
