import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../participant/auth';

export function AdminProtectedRoute({ children }: { children: ReactNode }) {
  const { state, checkSession } = useAuth();
  const checkingExpiry = useRef(false);
  useEffect(() => {
    const expired = () => {
      if (checkingExpiry.current) return;
      checkingExpiry.current = true;
      void checkSession().finally(() => { checkingExpiry.current = false; });
    };
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, [checkSession]);
  if (state.status === 'checking') return <main className="admin-placeholder" role="status">Checking your session…</main>;
  if (state.status === 'error') return <main className="admin-placeholder"><p role="alert">We couldn’t verify your session.</p><button className="admin-text-button" onClick={() => void checkSession()}>Try again</button></main>;
  if (state.status !== 'authenticated' || state.identity.role !== 'ADMIN') return <Navigate to="/admin/login" replace />;
  return <>{children}</>;
}
export function AdminPlaceholder() {
  const { state, logout } = useAuth();
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function signOut() { if (busy) return; setBusy(true); setError(''); try { await logout(); } catch { setError('Unable to sign out. Please try again.'); } finally { setBusy(false); } }
  if (state.status !== 'authenticated' || state.identity.role !== 'ADMIN') return null;
  return <main className="admin-placeholder"><p className="admin-placeholder-brand">THE BIG BANG</p><h1>Administrator</h1><p>Signed in as <strong>{state.identity.username}</strong>.</p><p>The administrator dashboard is coming soon.</p>{error && <p role="alert">{error}</p>}<button className="admin-submit" type="button" disabled={busy} onClick={() => void signOut()}>{busy ? 'Signing out…' : 'Sign out'}</button></main>;
}
