import { test } from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { fileURLToPath, URL } from 'node:url';
import { Buffer } from 'node:buffer';
const result = await build({ entryPoints: [fileURLToPath(new URL('../src/features/participant/homeState.ts', import.meta.url))], bundle: true, write: false, platform: 'node', format: 'esm' });
const { formatNumber, drawLabel, notification } = await import(`data:text/javascript;base64,${Buffer.from(result.outputFiles[0].text).toString('base64')}`);
test('participant number uses a minimum of three digits', () => {
  assert.equal(formatNumber(1), '#001'); assert.equal(formatNumber(42), '#042'); assert.equal(formatNumber(1000), '#1000');
});
test('draw status reflects actual current cycle and active selection', () => {
  assert.equal(drawLabel({selectedInCurrentCycle:false, currentlySelected:false}), 'Eligible');
  assert.equal(drawLabel({selectedInCurrentCycle:true, currentlySelected:false}), 'Selected this cycle');
  assert.equal(drawLabel({selectedInCurrentCycle:true, currentlySelected:true, drawStatus:'ACTIVE'}), 'Waiting for the wheel');
});
test('only supported private notifications produce messages', () => {
  assert.equal(notification('PRIZE_WON', {prizeName:'Book'}), 'You won Book!');
  assert.equal(notification('PARTICIPANT_SELECTED', {}), 'You have been selected!');
  assert.equal(notification('PRIZE_WON', {}), null);
  assert.equal(notification('DRAW_RESET', {}), null);
});
