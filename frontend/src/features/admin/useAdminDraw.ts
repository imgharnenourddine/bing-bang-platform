import { useCallback, useEffect, useRef, useState } from 'react';
import { realtime, type RealtimeStatus } from '../../services/realtime';
import { createSubmissionGate } from '../participant/registrationApi';
import { fetchDrawStatus, fetchDrawHistory, fetchParticipantCount, startDraw, resetDraw, cancelDraw, relevantEvent, type DrawState, type DrawHistory } from './adminDashboardApi';

type Load<T> = {status:'loading'} | {status:'error'} | {status:'ready';data:T};
type Mutation='start'|'cancel'|'reset';
export function useAdminDraw() {
  const [draw,setDraw]=useState<Load<DrawState>>({status:'loading'}); const [count,setCount]=useState<Load<number>>({status:'loading'}); const [history,setHistory]=useState<Load<DrawHistory>>({status:'loading'});
  const [connection,setConnection]=useState<RealtimeStatus>('connecting'); const [pending,setPending]=useState(false); const [error,setError]=useState(''); const [page,setPage]=useState(0); const [expanded,setExpanded]=useState(false);
  const revision=useRef(0); const gate=useRef(createSubmissionGate()); const pageRef=useRef(page); const expandedRef=useRef(expanded); pageRef.current=page; expandedRef.current=expanded;
  const refresh=useCallback(() => { const n=++revision.current; const size=expandedRef.current?20:5;
    void fetchDrawStatus().then(data=>{if(n===revision.current)setDraw({status:'ready',data});},()=>{if(n===revision.current)setDraw({status:'error'});});
    void fetchParticipantCount().then(data=>{if(n===revision.current)setCount({status:'ready',data});},()=>{if(n===revision.current)setCount({status:'error'});});
    void fetchDrawHistory(pageRef.current,size).then(data=>{if(n===revision.current)setHistory({status:'ready',data});},()=>{if(n===revision.current)setHistory({status:'error'});});
  },[]);
  useEffect(()=>{ const current=revision; const seen=new Set<string>(); let timer:ReturnType<typeof setTimeout>|undefined;
    const schedule=()=>{if(timer)clearTimeout(timer);timer=setTimeout(refresh,200);};
    const receive=(body:string)=>{try{const event=JSON.parse(body) as {eventId?:string;type?:string};if(!event.eventId||!event.type||seen.has(event.eventId))return;seen.add(event.eventId);if(seen.size>200)seen.delete(seen.values().next().value!);if(relevantEvent(event.type))schedule();}catch{/* malformed messages are ignored */}};
    const a=realtime.subscribe('/topic/admin',m=>receive(m.body));const b=realtime.subscribe('/topic/draw',m=>receive(m.body));const c=realtime.onConnected(schedule);const d=realtime.onStatus(setConnection);realtime.start();refresh();
    return ()=>{current.current++;if(timer)clearTimeout(timer);a();b();c();d();};
  },[refresh]);
  const changePage=useCallback((next:number,all:boolean)=>{pageRef.current=next;expandedRef.current=all;setPage(next);setExpanded(all);setHistory({status:'loading'});refresh();},[refresh]);
  const mutate=useCallback(async (action:Mutation)=>{
    if(draw.status!=='ready'||pending||connection!=='connected')return;
    if(action==='start'&&(!draw.data.canStartDraw||draw.data.activeDraw))return;
    if(action==='cancel'&&!draw.data.activeDraw)return;
    if(action==='reset'&&(!draw.data.canReset||draw.data.activeDraw))return;
    if(action==='cancel'&&!window.confirm('Cancel this selection? The participant will remain selected for the current cycle and cannot be selected again until the cycle is reset.'))return;
    if(action==='reset'&&!window.confirm('Start a new draw cycle? Previously selected participants become eligible again. Draw history and awarded prizes are preserved. Prize inventory and premium progression are not reset.'))return;
    if(!gate.current.start())return;
    const snapshot=draw.data; revision.current++; setPending(true);setError('');setDraw({status:'loading'});
    try { if(action==='start')await startDraw();else if(action==='cancel')await cancelDraw(snapshot.activeDraw!.id);else await resetDraw(snapshot.cycleNumber); }
    catch {setError('The request could not be confirmed. The current draw state is being refreshed before another action.');}
    finally {setDraw({status:'loading'});refresh();gate.current.finish();setPending(false);}
  },[draw,pending,connection,refresh]);
  return {draw,count,history,connection,pending,error,refresh,mutate,page,expanded,changePage};
}
