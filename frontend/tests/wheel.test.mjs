import { test } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';
async function load(entry,plugins=[]){const r=await build({entryPoints:[fileURLToPath(new URL(entry,import.meta.url))],bundle:true,write:false,platform:'node',format:'esm',plugins});return import(`data:text/javascript;base64,${Buffer.from(r.outputFiles[0].text).toString('base64')}`);}
const geometry=await load('../src/features/admin/wheelGeometry.ts');
test('pointer lands at center of each server-selected segment across rotations',()=>{for(let count=1;count<=40;count++)for(let index=0;index<count;index++)for(const current of [0,17,4000]){const angle=geometry.targetRotation(current,index,count);const midpoint=(index+.5)*360/count;assert.ok(Math.abs(((angle+midpoint)%360))<1e-8 || Math.abs(((angle+midpoint)%360)-360)<1e-8);assert.ok(angle>=current+1800);assert.ok(geometry.segmentPath(index,count));}});
test('invalid segment selection is rejected',()=>{assert.throws(()=>geometry.targetRotation(0,0,0));assert.throws(()=>geometry.targetRotation(0,5,5));});
const api=await load('../src/features/admin/adminWheelApi.ts',[{name:'mock-api',setup(builder){builder.onResolve({filter:/services\/api$/},()=>({path:'api',namespace:'mock'}));builder.onLoad({filter:/.*/,namespace:'mock'},()=>({contents:'export const api={get:(...args)=>globalThis.__wheelGet(...args),post:(...args)=>globalThis.__wheelPost(...args)};',loader:'js'}));}}]);
test('spin uses only draw ID and returns the backend award without retries',async()=>{let calls=0;const award={awardId:'a',drawId:'d',prizeId:'p',prizeName:'Prize',segments:[]};globalThis.__wheelPost=async(...args)=>{calls++;assert.deepEqual(args,['/admin/wheel/spin',{drawId:'d'}]);return {data:award};};assert.deepEqual(await api.spinWheel('d'),award);assert.equal(calls,1);});
test('recovery searches history for the original draw',async()=>{const award={drawId:'d',prizeName:'Prize'};globalThis.__wheelGet=async(...args)=>{assert.deepEqual(args,['/admin/wheel/history',{params:{draw:'d',page:0,size:1}}]);return {data:{content:[award]}};};assert.deepEqual(await api.findAward('d'),award);});
test('draw and inventory events cause resynchronization',()=>{for(const type of ['DRAW_RESET','DRAW_COMPLETED','PRIZE_AWARDED','PRIZE_INVENTORY_UPDATED','WHEEL_STATE_UPDATED'])assert.equal(api.relevantWheelEvent(type),true);assert.equal(api.relevantWheelEvent('QUESTION_OPENED'),false);});
