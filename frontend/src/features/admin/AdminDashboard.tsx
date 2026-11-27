import { CheckCircle2, Gift, Play, Shuffle, UserRoundCheck, UsersRound } from 'lucide-react';
import { AdminLayout } from './AdminLayout';
import { useAdminDashboard } from './useAdminDashboard';
import { drawDescription } from './adminDashboardApi';

type Value = {status:'loading'|'error'} | {status:'ready';data:number};
function StatCard({label,value,accent,icon:Icon}:{label:string;value:Value;accent:string;icon:typeof Gift}) {
  return <section className={`stat-card ${accent}`} aria-label={label}><Icon aria-hidden="true"/><div><h2>{label}</h2><p>{value.status==='ready'?value.data.toLocaleString():value.status==='loading'?'Loading…':'Unavailable'}</p></div></section>;
}
export function AdminDashboard() {
  const {participants,draw,prizes,connection,starting,drawError,refresh,start}=useAdminDashboard();
  const selected:Value=draw.status==='ready'?{status:'ready',data:draw.data.selectedCount}:{status:draw.status};
  const eligible:Value=draw.status==='ready'?{status:'ready',data:draw.data.eligibleCount}:{status:draw.status};
  const canStart=draw.status==='ready' && draw.data.canStartDraw && !draw.data.activeDraw && connection==='connected' && !starting;
  const hasError=participants.status==='error'||draw.status==='error'||prizes.status==='error';
  return <AdminLayout><main className="dashboard-main"><div className="stats-grid"><StatCard label="Total Participants" value={participants} accent="purple" icon={UsersRound}/><StatCard label="Selected Participants" value={selected} accent="yellow" icon={CheckCircle2}/><StatCard label="Eligible Participants" value={eligible} accent="blue" icon={UserRoundCheck}/><StatCard label="Available Prizes" value={prizes} accent="red" icon={Gift}/></div>
    <section className="draw-panel" aria-labelledby="draw-status-title"><Shuffle className="draw-panel-icon" aria-hidden="true"/><div className="draw-panel-copy"><h2 id="draw-status-title">Draw Status</h2><p>{draw.status==='ready'?drawDescription(draw.data):draw.status==='loading'?'Loading current draw…':'Draw status is unavailable.'}</p>{draw.status==='ready' && draw.data.activeDraw && <p className="draw-extra">The selected participant is waiting for the prize wheel. Draw management is coming soon.</p>}{drawError && <p role="alert" className="dashboard-error">{drawError}</p>}</div><button className="dashboard-start" type="button" disabled={!canStart} onClick={() => void start()}><Play aria-hidden="true"/> {starting?'Starting…':'Start a Draw'} <span aria-hidden="true">→</span></button></section>
    {connection!=='connected' && <p className="dashboard-notice" role="status">{connection==='connecting'?'Connecting to live updates…':'Live updates unavailable.'} Draw controls are paused until reconnected.</p>}{hasError && <div className="dashboard-notice" role="alert">Some data could not be loaded. <button onClick={refresh}>Retry</button></div>}
  </main></AdminLayout>;
}
