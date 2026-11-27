import { useRef, useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { ArrowRight, Eye, EyeOff, LockKeyhole, ShieldCheck, UserRound } from 'lucide-react';
import { ApiError } from '../../services/api';
import { useAuth } from '../participant/auth';
import { createSubmissionGate } from '../participant/registrationApi';
import logos from '../../assets/participant/official-header.webp';
import artwork from '../../assets/participant/poster-art.webp';
import { ParticipantSessionError, validateCredentials } from './adminApi';
import './adminLogin.css';

function message(error: unknown): string {
  if (error instanceof ParticipantSessionError) return 'A participant session is active. Sign out before continuing.';
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Incorrect username or password.';
    if (error.status === 429) return 'Too many attempts. Please wait and try again.';
    if (error.status === 403) return 'Your session or security token has expired. Refresh the page and try again.';
    if (error.status === 400) return 'Please check your username and password.';
  }
  return 'Unable to sign in right now. Check your connection and try again.';
}
export function AdminLoginPage() {
  const { state, checkSession, login, logout } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState({ username: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const gate = useRef(createSubmissionGate());
  if (state.status === 'authenticated' && state.identity.role === 'ADMIN') return <Navigate to="/admin" replace />;
  const participant = state.status === 'authenticated' && state.identity.role === 'PARTICIPANT';
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state.status !== 'anonymous') return;
    const fields = validateCredentials(username, password); setErrors(fields);
    if (fields.username || fields.password || !gate.current.start()) return;
    setError(''); setBusy(true);
    try { await login(username.trim(), password); setPassword(''); }
    catch (err) { setError(message(err)); }
    finally { gate.current.finish(); setBusy(false); }
  }
  async function signOut() {
    if (!gate.current.start()) return;
    setBusy(true); setError('');
    try { await logout(); } catch { setError('Unable to sign out. Check your connection and try again.'); }
    finally { gate.current.finish(); setBusy(false); }
  }
  return <main className="admin-login-page"><div className="admin-login-canvas">
    <header className="admin-brand"><img className="admin-logos" src={logos} alt="N7 Geeks, Google Developer Groups On Campus, UH2C ENSET, AWS Cloud Clubs and FDE" /><h1>THE BIG BANG</h1><p>Three worlds. One beginning.</p></header>
    <div className="admin-art" aria-hidden="true"><img src={artwork} alt="" /></div>
    <section className="admin-login-card" aria-labelledby="admin-login-title">
      <div className="admin-card-heading"><ShieldCheck aria-hidden="true" /><div><h2 id="admin-login-title">Administrator Access</h2><p>Manage the event, draws, prizes, and live sessions.</p></div></div>
      {state.status === 'checking' ? <p role="status" className="admin-status">Checking your session…</p> : state.status === 'error' ? <div className="admin-status" role="alert"><p>We couldn’t check your session. Please try again.</p><button className="admin-text-button" type="button" onClick={() => void checkSession()}>Try again</button></div> : participant ? <div className="admin-status"><p>A participant session is active. Sign out before signing in as an administrator.</p><button className="admin-text-button" disabled={busy} type="button" onClick={() => void signOut()}>{busy ? 'Signing out…' : 'Sign out of participant session'}</button>{error && <p role="alert">{error}</p>}</div> : <form onSubmit={event => void submit(event)} noValidate>
        <label className="admin-field"><UserRound aria-hidden="true"/><span className="admin-sr-only">Username</span><input autoComplete="username" name="username" placeholder="Username" value={username} maxLength={100} aria-required="true" aria-invalid={!!errors.username} aria-describedby={errors.username ? 'admin-user-error' : undefined} onChange={event => { setUsername(event.target.value); setErrors(old => ({...old, username: ''})); }}/></label>
        {errors.username && <p className="admin-error" id="admin-user-error" role="alert">{errors.username}</p>}
        <label className="admin-field"><LockKeyhole aria-hidden="true"/><span className="admin-sr-only">Password</span><input autoComplete="current-password" name="password" placeholder="Password" type={showPassword ? 'text' : 'password'} value={password} maxLength={72} aria-required="true" aria-invalid={!!errors.password} aria-describedby={errors.password ? 'admin-password-error' : undefined} onChange={event => { setPassword(event.target.value); setErrors(old => ({...old, password: ''})); }}/><button type="button" className="password-toggle" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? <EyeOff /> : <Eye />}</button></label>
        {errors.password && <p className="admin-error" id="admin-password-error" role="alert">{errors.password}</p>}
        {error && <p className="admin-error" role="alert">{error}</p>}
        <button className="admin-submit" type="submit" disabled={busy}>{busy ? 'Signing in…' : <>Sign In <ArrowRight aria-hidden="true" /></>}</button>
        {busy && <span className="admin-sr-only" role="status">Signing in, please wait.</span>}
      </form>}
    </section>
  </div></main>;
}
