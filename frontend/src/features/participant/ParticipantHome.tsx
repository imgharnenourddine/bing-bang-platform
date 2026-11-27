import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { BadgeCheck, Bell, CalendarDays, CircleHelp, Clock3, Gift, House, MapPin, Ticket, UsersRound } from 'lucide-react';
import headerLogos from '../../assets/participant/official-header.webp';
import artwork from '../../assets/participant/poster-art.webp';
import n7 from '../../assets/participant/club-n7.webp';
import gdg from '../../assets/participant/club-gdg.webp';
import aws from '../../assets/participant/club-aws.webp';
import { useAuth } from './auth';
import { drawLabel, formatNumber } from './homeState';
import { useParticipantActivity } from './useParticipantActivity';
import type { ParticipantIdentity } from './registrationApi';
import './participantHome.css';

export function ParticipantHeader() {
  return <header className="home-header"><img className="home-partners" src={headerLogos} alt="N7 Geeks, Google Developer Groups On Campus, UH2C ENSET, AWS Cloud Clubs and FDE" /><div className="home-brand"><h1>THE BIG BANG</h1><p>Three worlds. One beginning.</p></div><img className="home-art" src={artwork} alt="" /></header>;
}
export function ParticipantBottomNavigation() {
  return <nav className="bottom-nav" aria-label="Participant navigation"><Link to="/participant" aria-current="page"><House /><span>Home</span></Link><a href="#quiz"><CircleHelp /><span>Quiz</span></a><a href="#draw"><Gift /><span>Draw</span></a><a href="#pass"><Ticket /><span>My Pass</span></a></nav>;
}
export function ParticipantPass({ participant }: { participant: ParticipantIdentity }) {
  return <section className="pass-section" id="pass" aria-labelledby="pass-title"><div className="pass-top"><div className="pass-heading"><Ticket aria-hidden="true" /><h2 id="pass-title">Participant Pass</h2><span className="registered"><BadgeCheck aria-hidden="true" /> REGISTERED</span></div><div className="pass-identity"><strong>{formatNumber(participant.participantNumber)}</strong><span>{participant.firstName} {participant.lastName}</span></div></div><div className="pass-details"><div className="ticket-emblem" aria-label="Event ticket"><Ticket aria-hidden="true" /></div><div className="event-facts"><p><CalendarDays /> Nov 28, 2026</p><p><Clock3 /> 13:00</p><p><MapPin /> ENSET Mohammedia<br /> Amphitheater</p></div></div></section>;
}
function Clubs() { return <section className="clubs-section" aria-labelledby="clubs-title"><h2 id="clubs-title"><UsersRound /> Clubs &amp; Organizers</h2><div className="club-grid"><div><img src={n7} alt="N7 Geeks" /></div><div><img src={gdg} alt="Google Developer Groups On Campus" /></div><div><img src={aws} alt="AWS Cloud Clubs" /></div></div></section>; }
function HomeContent({ participant }: { participant: ParticipantIdentity }) {
  const location = useLocation(); const navigate = useNavigate();
  const [confirmation] = useState(() => Boolean((location.state as { registered?: boolean } | null)?.registered));
  const { draw, connection, notice, refresh } = useParticipantActivity();
  useEffect(() => { if ((location.state as { registered?: boolean } | null)?.registered) navigate(location.pathname, { replace: true, state: null }); }, [location, navigate]);
  return <div className="home-page"><div className="home-canvas"><ParticipantHeader /><main className="home-content">
    {confirmation && <div className="success-banner" role="status"><BadgeCheck /><div><strong>Registration Successful!</strong><p>You are now a participant in THE BIG BANG.</p></div></div>}
    <ParticipantPass participant={participant} /><Clubs />
    <div className="activity-grid"><section className="activity quiz-card" id="quiz"><div className="activity-heading"><CircleHelp /><div><h2>Quiz</h2><strong>Unavailable</strong></div></div><p>The Quiz interface is not available yet.</p></section>
      <section className="activity draw-card" id="draw"><div className="activity-heading"><Gift /><div><h2>Lucky Draw</h2><strong>{draw.status === 'ready' ? drawLabel(draw.data) : draw.status === 'loading' ? 'Loading…' : 'Unavailable'}</strong></div></div><p>{draw.status === 'ready' ? draw.data.currentlySelected && draw.data.drawStatus === 'ACTIVE' ? 'You have been selected. Please wait for the wheel.' : draw.data.selectedInCurrentCycle ? 'You have already been selected in this cycle.' : 'You are eligible for the random draw.' : 'Draw status could not be confirmed.'}</p>{draw.status === 'error' && <button className="home-retry" onClick={() => void refresh()}>Retry</button>}</section></div>
    <section className="notification-panel" aria-live="polite"><Bell aria-hidden="true" /><div><h2>Selection Notification</h2><p>{notice ?? 'You will receive a notification here when you are selected or win a prize.'}</p>{notice && <a href="#draw">View draw status →</a>}</div></section>
    {connection !== 'connected' && <p className="connection-status" role="status">{connection === 'connecting' ? 'Reconnecting to live updates…' : 'Live updates unavailable. Checking draw status periodically.'}</p>}
    <p className="feature-note">Quiz and draw screens are coming soon.</p>
  </main><ParticipantBottomNavigation /></div></div>;
}
export function ParticipantHome() {
  const { state, checkSession } = useAuth();
  if (state.status === 'checking') return <main className="placeholder-page" role="status">Checking your session…</main>;
  if (state.status === 'error') return <main className="placeholder-page"><p role="alert">We couldn’t check your session.</p><button className="text-action" onClick={() => void checkSession()}>Try again</button></main>;
  if (state.status !== 'authenticated' || state.identity.role !== 'PARTICIPANT') return <Navigate to="/" replace />;
  return <HomeContent participant={state.identity} />;
}
