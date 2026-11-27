import { useState, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { CircleHelp, ClipboardList, Gift, LayoutDashboard, LogOut, Menu, ShieldCheck, Shuffle, UsersRound, X, CircleDot } from 'lucide-react';
import { useAuth } from '../participant/auth';
import logos from '../../assets/participant/official-header.webp';
import artwork from '../../assets/participant/poster-art.webp';
import './adminDashboard.css';

export function AdminLayout({children, page = 'dashboard'}:{children:ReactNode; page?: 'dashboard' | 'draw' | 'wheel'}) {
  const { logout }=useAuth(); const [open,setOpen]=useState(false); const [busy,setBusy]=useState(false); const [error,setError]=useState(''); const [unavailable,setUnavailable]=useState('');
  async function signOut() { if(busy) return; setBusy(true); setError(''); try { await logout(); } catch { setError('Unable to sign out. Please try again.'); } finally { setBusy(false); } }
  const upcoming=[{label:'Prizes',icon:Gift},{label:'Quiz',icon:ClipboardList}];
  return <div className="admin-shell"><button className="admin-menu-toggle" aria-expanded={open} aria-controls="admin-navigation" aria-label={open ? 'Close navigation' : 'Open navigation'} onClick={() => setOpen(value=>!value)}>{open ? <X/> : <Menu/>}</button>
    <aside className={`admin-sidebar ${open?'is-open':''}`} id="admin-navigation"><div className="sidebar-brand">THE<br/>BIG BANG<span /></div><div className="sidebar-label"><ShieldCheck/> Admin Panel</div><nav aria-label="Administrator navigation"><NavLink to="/admin" end className={({isActive}) => isActive ? "sidebar-active" : ""} onClick={() => setOpen(false)}><LayoutDashboard/> Dashboard</NavLink><button type="button" onClick={() => setUnavailable("Participants is not available yet.")}><UsersRound/> Participants<span className="admin-sr-only">, coming soon</span></button><NavLink to="/admin/draw" className={({isActive}) => isActive ? "sidebar-active" : ""} onClick={() => setOpen(false)}><Shuffle/> Lucky Draw</NavLink><NavLink to="/admin/wheel" className={({isActive}) => isActive ? "sidebar-active" : ""} onClick={() => setOpen(false)}><CircleDot/> Prize Wheel</NavLink>{upcoming.map(({label,icon:Icon})=><button key={label} type="button" onClick={() => setUnavailable(`${label} is not available yet.`)}><Icon/> {label}<span className="admin-sr-only">, coming soon</span></button>)}</nav>{unavailable && <p className="sidebar-notice" role="status"><CircleHelp size={16}/> {unavailable}</p>}<div className="sidebar-logout"><button type="button" disabled={busy} onClick={() => void signOut()}><LogOut/> {busy?'Signing out…':'Logout'}</button>{error && <p role="alert">{error}</p>}</div></aside>
    <div className="admin-workspace"><header className="dashboard-header"><img src={logos} alt="N7 Geeks, Google Developer Groups On Campus, UH2C ENSET, AWS Cloud Clubs and FDE"/></header>{page !== 'wheel' && <section className="dashboard-banner" aria-label="THE BIG BANG"><div className="banner-copy"><h1>{page === 'draw' ? 'Lucky Draw' : 'THE BIG BANG'}</h1><p>{page === 'draw' ? 'Random and transparent selection of participants.' : 'Three worlds. One beginning.'}</p><span/></div><img src={artwork} alt="" aria-hidden="true"/></section>}{children}</div>
  </div>;
}
