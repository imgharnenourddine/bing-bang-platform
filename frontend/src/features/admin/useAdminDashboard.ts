import { useCallback, useEffect, useRef, useState } from 'react';
import { realtime, type RealtimeStatus } from '../../services/realtime';
import { createSubmissionGate } from '../participant/registrationApi';
import { fetchParticipantCount, fetchDrawStatus, fetchAvailableStock, startDraw, relevantEvent, type DrawState } from './adminDashboardApi';

type Load<T> = { status: 'loading' | 'error' } | { status: 'ready'; data: T };
export function useAdminDashboard() {
  const [participants,setParticipants] = useState<Load<number>>({status:'loading'});
  const [draw,setDraw] = useState<Load<DrawState>>({status:'loading'});
  const [prizes,setPrizes] = useState<Load<number>>({status:'loading'});
  const [connection,setConnection] = useState<RealtimeStatus>('connecting');
  const [starting,setStarting] = useState(false);
  const [drawError,setDrawError] = useState('');
  const revision = useRef(0); const gate = useRef(createSubmissionGate());
  const refresh = useCallback(() => {
    const current=++revision.current;
    void fetchParticipantCount().then(data => { if(current===revision.current) setParticipants({status:'ready',data}); }, () => { if(current===revision.current) setParticipants({status:'error'}); });
    void fetchDrawStatus().then(data => { if(current===revision.current) setDraw({status:'ready',data}); }, () => { if(current===revision.current) setDraw({status:'error'}); });
    void fetchAvailableStock().then(data => { if(current===revision.current) setPrizes({status:'ready',data}); }, () => { if(current===revision.current) setPrizes({status:'error'}); });
  }, []);
  useEffect(() => {
    const requestRevision=revision; const seen=new Set<string>(); let timer:ReturnType<typeof setTimeout> | undefined;
    const schedule=() => { if(timer) clearTimeout(timer); timer=setTimeout(refresh,200); };
    const onMessage=(body:string) => { try { const event=JSON.parse(body) as {eventId?:string;type?:string}; if(!event.eventId || !event.type || seen.has(event.eventId)) return; seen.add(event.eventId); if(seen.size>200) seen.delete(seen.values().next().value!); if(relevantEvent(event.type)) schedule(); } catch { /* Ignore malformed events. */ } };
    const offAdmin=realtime.subscribe('/topic/admin', msg => onMessage(msg.body));
    const offDraw=realtime.subscribe('/topic/draw', msg => onMessage(msg.body));
    const offConnect=realtime.onConnected(schedule); const offStatus=realtime.onStatus(setConnection);
    realtime.start(); refresh();
    return () => { requestRevision.current++; if(timer) clearTimeout(timer); offAdmin(); offDraw(); offConnect(); offStatus(); };
  },[refresh]);
  const start = useCallback(async () => {
    if(draw.status!=='ready' || !draw.data.canStartDraw || draw.data.activeDraw || !gate.current.start()) return;
    setStarting(true); setDrawError('');
    revision.current++;
    // The prior snapshot must not enable another POST after an ambiguous response.
    setDraw({status:'loading'});
    try { await startDraw(); }
    catch { setDrawError('The draw could not be started. Check the current state before trying again.'); }
    finally { setDraw({status:'loading'}); refresh(); gate.current.finish(); setStarting(false); }
  },[draw,refresh]);
  return { participants, draw, prizes, connection, starting, drawError, refresh, start };
}
