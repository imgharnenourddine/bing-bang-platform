export interface DrawState { cycleNumber: number; selectedInCurrentCycle: boolean; currentlySelected: boolean; drawId: string | null; drawStatus: 'ACTIVE' | 'COMPLETED' | 'CANCELLED' | null }
export function formatNumber(number: number): string { return `#${String(number).padStart(3, '0')}`; }
export function drawLabel(draw: DrawState): string {
  if (draw.currentlySelected && draw.drawStatus === 'ACTIVE') return 'Waiting for the wheel';
  if (draw.selectedInCurrentCycle) return 'Selected this cycle';
  return 'Eligible';
}
export function notification(type: string, payload: unknown): string | null {
  if (!payload || typeof payload !== 'object') return null;
  const data = payload as Record<string, unknown>;
  if (type === 'PARTICIPANT_SELECTED') return 'You have been selected!';
  if (type === 'PRIZE_WON' && typeof data.prizeName === 'string' && data.prizeName.trim()) return `You won ${data.prizeName}!`;
  return null;
}
