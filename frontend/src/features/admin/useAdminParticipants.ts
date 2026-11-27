import { useCallback, useEffect, useRef, useState } from 'react';
import { realtime, type RealtimeStatus } from '../../services/realtime';
import { fetchAllParticipants,fetchParticipantTotal } from './adminParticipantsApi';
import type { ParticipantIdentity } from '../participant/registrationApi';
export function useAdminParticipants(){
 const [people,setPeople]=useState<ParticipantIdentity[]|null>(null);const [total,setTotal]=useState<number|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState('');const [connection,setConnection]=useState<RealtimeStatus>('connecting');const revision=useRef(0);
 const refresh=useCallback(async()=>{const n=++revision.current;const [list,count]=await Promise.allSettled([fetchAllParticipants(),fetchParticipantTotal()]);if(n!==revision.current)return;setLoading(false);if(list.status==='fulfilled')setPeople(list.value);if(count.status==='fulfilled')setTotal(count.value);setError(list.status==='rejected'||count.status==='rejected'?'Some participant data could not be refreshed. Displayed data may be out of date.':'');},[]);
 useEffect(()=>{const generation=revision;const seen=new Set<string>();let timer:ReturnType<typeof setTimeout>|undefined;const schedule=()=>{if(timer)clearTimeout(timer);timer=setTimeout(()=>void refresh(),200);};const off=realtime.subscribe('/topic/admin',message=>{try{const event=JSON.parse(message.body) as {eventId?:string;type?:string};if(event.type!=='PARTICIPANT_REGISTERED'||!event.eventId||seen.has(event.eventId))return;seen.add(event.eventId);if(seen.size>200)seen.delete(seen.values().next().value!);schedule();}catch{/* Ignore malformed messages. */}});const connected=realtime.onConnected(schedule);const status=realtime.onStatus(setConnection);realtime.start();void refresh();return()=>{generation.current++;if(timer)clearTimeout(timer);off();connected();status();};},[refresh]);
 return {people,total,loading,error,connection,refresh};
}
