import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Gift, Play } from 'lucide-react';
import { AdminLayout } from './AdminLayout';
import { usePrizeWheel } from './usePrizeWheel';
import { labelPosition, segmentPath } from './wheelGeometry';
import './prizeWheel.css';

export function PrizeWheelPage() {
 const {status,loading,error,connection,phase,snapshot,rotation,duration,award,spin,dismiss,retry}=usePrizeWheel();
 const dialog=useRef<HTMLDialogElement>(null);
 useEffect(()=>{if(phase==='result'&&award&&!dialog.current?.open) dialog.current?.showModal();},[phase,award]);
 const segments=snapshot??status?.eligiblePrizes??[];
 const colors=['#8b20d5','#1565c0','#f4bf14','#f6f1e4'];
 const ready=phase==='idle'&&status?.canSpin&&status.activeDraw&&segments.length>0&&connection==='connected';
 return <AdminLayout page="wheel"><main className="wheel-page"><div className="wheel-heading"><h1>Prize Wheel</h1><p>Spin the wheel to assign a prize.</p></div>
  <div className="wheel-stage"><div className="wheel-pointer" aria-hidden="true"/><svg className="wheel-svg" viewBox="0 0 400 400" role="img" aria-label={segments.length?`Prize wheel with ${segments.length} eligible prizes`:'No eligible prizes on the wheel'}>
   <g style={{transform:`rotate(${rotation}deg)`,transformOrigin:'200px 200px',transition:duration?`transform ${duration}ms cubic-bezier(.12,.62,.18,1)`:'none'}}>
    {segments.length===0?<circle cx="200" cy="200" r="190" fill="#eee5d6"/>:segments.map((segment,index)=>{const p=labelPosition(index,segments.length);return <g key={segment.prizeId}><path d={segmentPath(index,segments.length)} fill={colors[index%colors.length]} stroke="#f6f1e4" strokeWidth="1"/><text x={p.x} y={p.y} textAnchor="middle" dominantBaseline="middle" fill={index%4<2?'white':'#121110'} fontSize={segments.length>12?8:12} fontWeight="700"><title>{segment.name}</title>{segment.name.length>15?`${segment.name.slice(0,14)}…`:segment.name}</text></g>;})}
   </g><circle cx="200" cy="200" r="190" fill="none" stroke="#f6f1e4" strokeWidth="12"/><circle cx="200" cy="200" r="37" fill="#f6f1e4"/><g transform="translate(179 179)" stroke="#5d1299" fill="none" strokeWidth="2.4"><Gift width="42" height="42"/></g>
  </svg></div>
  <p className="wheel-note">Segment sizes are equal; winning chances are weighted by the backend.</p>
  <button className="wheel-spin" disabled={!ready} onClick={()=>void spin()}><Play aria-hidden="true"/> {phase==='request'?'Allocating…':phase==='animation'?'Spinning…':'Spin the Wheel'}</button>
  <Link className="wheel-back" to="/admin/draw">← &nbsp; Back to Lucky Draw</Link>
  {loading&&<p className="wheel-message" role="status">Loading wheel…</p>}
  {!loading&&phase==='idle'&&status&&!status.activeDraw&&<p className="wheel-message">No active draw. Select a participant first.</p>}
  {!loading&&phase==='idle'&&status?.activeDraw&&!status.eligiblePrizes.length&&<p className="wheel-message">No available prizes. Prize management is coming soon.</p>}
  {connection!=='connected'&&<p className="wheel-message" role="status">Live updates unavailable. Reconnecting…</p>}
  {error&&<p className="wheel-message wheel-error" role="alert">{error} {(phase==='recovery'||!status)&&<button onClick={retry}>Retry verification</button>}</p>}
  <dialog ref={dialog} className="wheel-result" onCancel={event=>{event.preventDefault();dialog.current?.close();dismiss();}}><Gift aria-hidden="true"/><h2>Congratulations!</h2><p>{award?.prizeName}</p><button onClick={()=>{dialog.current?.close();dismiss();}}>Continue</button></dialog>
 </main></AdminLayout>;
}
