import test from 'node:test'; import assert from 'node:assert/strict'; import {lineAt} from '../src/v2/lyrics-timing.js';
test('binary lookup handles before, duplicate, seek and after',()=>{const l=[{t:1},{t:2},{t:2},{t:9}];assert.equal(lineAt(l,0),-1);assert.equal(lineAt(l,2),2);assert.equal(lineAt(l,100),3);assert.equal(lineAt(l,1),0);assert.equal(lineAt([],2),-1)});
