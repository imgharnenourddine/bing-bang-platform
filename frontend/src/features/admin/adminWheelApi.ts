import { api } from '../../services/api';
export interface WheelSegment { prizeId:string; name:string; category:string; remainingStock:number; }
export interface WheelStatus { activeDraw:null|{id:string;status:string}; eligiblePrizes:WheelSegment[]; canSpin:boolean }
export interface WheelAward { awardId:string;drawId:string;prizeId:string;prizeName:string;segments:WheelSegment[] }
export async function fetchWheel():Promise<WheelStatus> { return (await api.get<WheelStatus>('/admin/wheel/status')).data; }
export async function spinWheel(drawId:string):Promise<WheelAward> { return (await api.post<WheelAward>('/admin/wheel/spin',{drawId})).data; }
export async function findAward(drawId:string):Promise<WheelAward|null> { const result=await api.get<{content:WheelAward[]}>('/admin/wheel/history',{params:{draw:drawId,page:0,size:1}});return result.data.content[0]??null; }
export function relevantWheelEvent(type:string):boolean {return ['DRAW_STARTED','DRAW_PARTICIPANT_SELECTED','DRAW_CANCELLED','DRAW_COMPLETED','DRAW_RESET','DRAW_STATE_UPDATED','WHEEL_STATE_UPDATED','WHEEL_RESULT','PRIZE_INVENTORY_UPDATED','PRIZE_AWARDED'].includes(type);}
