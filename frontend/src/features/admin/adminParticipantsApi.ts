import { api } from '../../services/api';
import { formatNumber } from '../participant/homeState';
import type { ParticipantIdentity } from '../participant/registrationApi';
export interface ParticipantPage {content:ParticipantIdentity[];number:number;totalPages:number;last:boolean}
export async function fetchAllParticipants():Promise<ParticipantIdentity[]> {
 const participants=new Map<string,ParticipantIdentity>();let page=0;
 for (;;) {
  const result=(await api.get<ParticipantPage>('/admin/participants',{params:{page,size:100}})).data;
  if(!Array.isArray(result.content)||result.number!==page||!Number.isInteger(result.totalPages)||typeof result.last!=='boolean')throw new Error('Invalid participant page');
  for(const participant of result.content)participants.set(participant.id,participant);
  if(result.last||result.totalPages===0)break;
  if(result.content.length===0||page>=9999)throw new Error('Incomplete participant pagination');
  page++;
 }
 return [...participants.values()].sort((a,b)=>a.participantNumber-b.participantNumber);
}
export async function fetchParticipantTotal():Promise<number>{return (await api.get<{count:number}>('/admin/participants/count')).data.count;}
export function filterParticipants(people:ParticipantIdentity[],query:string):ParticipantIdentity[]{const q=query.trim().toLocaleLowerCase().replace(/^#/,'');if(!q)return people;return people.filter(p=>(String(p.participantNumber).includes(q)||formatNumber(p.participantNumber).slice(1).includes(q))||`${p.firstName} ${p.lastName}`.toLocaleLowerCase().includes(q)||`${p.lastName} ${p.firstName}`.toLocaleLowerCase().includes(q));}
export function paginateParticipants(people:ParticipantIdentity[],page:number,size=20){const totalPages=Math.ceil(people.length/size);const current=Math.max(0,Math.min(page,Math.max(0,totalPages-1)));return {content:people.slice(current*size,(current+1)*size),page:current,totalPages};}
