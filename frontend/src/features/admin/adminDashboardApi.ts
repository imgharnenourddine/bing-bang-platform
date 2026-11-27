import { api } from '../../services/api';
export interface DrawHistoryEntry { id:string; cycleNumber:number; status:'ACTIVE'|'COMPLETED'|'CANCELLED'; createdAt:string; participant:{participantNumber:number;firstName:string;lastName:string} }
export interface DrawState { cycleNumber: number; registeredCount: number; selectedCount: number; eligibleCount: number; canStartDraw: boolean; canReset: boolean; activeDraw: null | { id: string; status: 'ACTIVE' | 'COMPLETED' | 'CANCELLED'; participant: { participantNumber: number; firstName: string; lastName: string } } }
export interface Prize { enabled: boolean; remainingStock: number }
export function availableStock(prizes: Prize[]): number { return prizes.filter(prize => prize.enabled && prize.remainingStock > 0).reduce((total, prize) => total + prize.remainingStock, 0); }
export async function fetchParticipantCount(): Promise<number> { return (await api.get<{count:number}>('/admin/participants/count')).data.count; }
export async function fetchDrawStatus(): Promise<DrawState> { return (await api.get<DrawState>('/admin/draw/status')).data; }
export async function fetchAvailableStock(): Promise<number> { return availableStock((await api.get<Prize[]>('/admin/prizes')).data); }
export async function startDraw(): Promise<void> { await api.post('/admin/draw/start'); }
export function drawDescription(draw: DrawState): string {
  if (draw.activeDraw) return `Selected: #${String(draw.activeDraw.participant.participantNumber).padStart(3, '0')} · ${draw.activeDraw.participant.firstName} ${draw.activeDraw.participant.lastName}`;
  if (!draw.registeredCount) return 'No participants are registered yet.';
  if (!draw.eligibleCount) return 'No eligible participants remain in this cycle.';
  return 'No draw is currently running.';
}
export function relevantEvent(type: string): boolean { return ['PARTICIPANT_REGISTERED','DRAW_STARTED','DRAW_PARTICIPANT_SELECTED','DRAW_COMPLETED','DRAW_CANCELLED','DRAW_RESET','DRAW_STATE_UPDATED','PRIZE_INVENTORY_UPDATED','PRIZE_AWARDED'].includes(type); }

export interface DrawHistory { content:DrawHistoryEntry[]; page:number; size:number; totalElements:number }
export async function fetchDrawHistory(page:number, size:number): Promise<DrawHistory> { return (await api.get<DrawHistory>('/admin/draw/history',{params:{page,size}})).data; }
export async function cancelDraw(id:string): Promise<void> { await api.post(`/admin/draw/${encodeURIComponent(id)}/cancel`); }
export async function resetDraw(cycleNumber:number): Promise<void> { await api.post('/admin/draw/reset',{confirmed:true,expectedCycleNumber:cycleNumber}); }
