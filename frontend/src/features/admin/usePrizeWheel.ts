import { useCallback, useEffect, useRef, useState } from 'react';
import { realtime, type RealtimeStatus } from '../../services/realtime';
import { createSubmissionGate } from '../participant/registrationApi';
import { fetchWheel, findAward, relevantWheelEvent, spinWheel, type WheelAward, type WheelSegment, type WheelStatus } from './adminWheelApi';
import { targetRotation } from './wheelGeometry';

const RECOVERY_KEY='bigbang.pendingWheelDraw';
function pendingDraw():string|null { try{return sessionStorage.getItem(RECOVERY_KEY);}catch{return null;} }
function savePending(id:string|null):boolean {try{if(id)sessionStorage.setItem(RECOVERY_KEY,id);else sessionStorage.removeItem(RECOVERY_KEY);return true;}catch{return false;} }
export function usePrizeWheel() {
 const [status,setStatus]=useState<WheelStatus|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState('');
 const [connection,setConnection]=useState<RealtimeStatus>('connecting');const [phase,setPhase]=useState<'idle'|'request'|'animation'|'result'|'recovery'>('idle');
 const [snapshot,setSnapshot]=useState<WheelSegment[]|null>(null);const [rotation,setRotation]=useState(0);const [duration,setDuration]=useState(0);const [award,setAward]=useState<WheelAward|null>(null);
 const gate=useRef(createSubmissionGate());const busy=useRef(false);const revision=useRef(0);const angle=useRef(0);const deferred=useRef(false);const timer=useRef<ReturnType<typeof setTimeout>|undefined>(undefined);const live=useRef(true);
 const refresh=useCallback(async()=>{if(busy.current){deferred.current=true;return;}const n=++revision.current;try{const data=await fetchWheel();if(live.current&&n===revision.current){setStatus(data);setLoading(false);}}catch{if(live.current&&n===revision.current){setStatus(null);setLoading(false);setError('Wheel state is unavailable. Please retry.');}}},[]);
 const recover=useCallback(async(id:string)=>{setPhase('recovery');busy.current=true;revision.current++;try{const found=await findAward(id);if(!live.current)return;if(found){setAward(found);setSnapshot(found.segments);setPhase('result');}else{savePending(null);setError('No award was found. Check the current draw before trying again.');setPhase('idle');busy.current=false;await refresh();}}catch{if(live.current){setError('Could not verify the previous spin. Retry verification before spinning.');setPhase('recovery');}}},[refresh]);
 useEffect(()=>{const requestRevision=revision;live.current=true;const prior=pendingDraw();if(prior)void recover(prior);else void refresh();const seen=new Set<string>();let debounce:ReturnType<typeof setTimeout>|undefined;
  const schedule=()=>{if(debounce)clearTimeout(debounce);debounce=setTimeout(()=>void refresh(),200);};
  const message=(body:string)=>{try{const event=JSON.parse(body) as {eventId?:string;type?:string};if(!event.eventId||!event.type||seen.has(event.eventId))return;seen.add(event.eventId);if(seen.size>200)seen.delete(seen.values().next().value!);if(relevantWheelEvent(event.type))schedule();}catch{/* Ignore invalid messages. */}};
  const a=realtime.subscribe('/topic/admin',m=>message(m.body));const b=realtime.subscribe('/topic/draw',m=>message(m.body));const c=realtime.subscribe('/topic/wheel',m=>message(m.body));const d=realtime.onConnected(schedule);const e=realtime.onStatus(setConnection);realtime.start();
  return()=>{live.current=false;requestRevision.current++;if(timer.current)clearTimeout(timer.current);if(debounce)clearTimeout(debounce);a();b();c();d();e();};
 },[refresh,recover]);
 const spin=useCallback(async()=>{if(!status?.activeDraw||!status.canSpin||status.eligiblePrizes.length===0||busy.current||connection!=='connected'||!gate.current.start())return;
  const id=status.activeDraw.id;const frozen=status.eligiblePrizes.map(segment=>({...segment}));busy.current=true;revision.current++;setError('');setPhase('request');setSnapshot(frozen);
  if(!savePending(id)){setError('Cannot safely recover a spin in this browser. Enable session storage and try again.');setSnapshot(null);setPhase('idle');busy.current=false;gate.current.finish();return;}
  try{const result=await spinWheel(id);if(!live.current)return;setAward(result);const index=frozen.findIndex(segment=>segment.prizeId===result.prizeId);
   if(index<0){setError('Inventory changed during selection. Showing the authoritative award.');setPhase('result');return;}
   const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;const next=targetRotation(angle.current,index,frozen.length);angle.current=next;setDuration(reduced?0:5000);setRotation(next);setPhase('animation');timer.current=setTimeout(()=>{if(live.current)setPhase('result');},reduced?0:5050);
  }catch{if(live.current){setError('Spin outcome is uncertain. Checking the saved award…');await recover(id);}}
  finally{gate.current.finish();}
 },[status,connection,recover]);
 const dismiss=useCallback(()=>{savePending(null);setAward(null);setSnapshot(null);setPhase('idle');setError('');busy.current=false;deferred.current=false;void refresh();},[refresh]);
 const retry=useCallback(()=>{setError('');const id=pendingDraw();if(id)void recover(id);else{busy.current=false;setPhase('idle');void refresh();}},[recover,refresh]);
 return {status,loading,error,connection,phase,snapshot,rotation,duration,award,spin,dismiss,retry};
}
