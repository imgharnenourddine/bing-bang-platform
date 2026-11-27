import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { fetchIdentity, registerParticipant, type Identity } from './registrationApi';
import { loginAdministrator, logoutSession, ParticipantSessionError } from '../admin/adminApi';
import { realtime } from '../../services/realtime';
import { ApiError } from '../../services/api';

type AuthState = { status: 'checking' | 'anonymous' | 'error' } | { status: 'authenticated'; identity: Identity };
interface AuthContextValue {
  state: AuthState;
  checkSession: () => Promise<void>;
  register: (firstName: string, lastName: string) => Promise<boolean>;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}
const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'checking' });
  const revision = useRef(0);
  const checkSession = useCallback(async () => {
    const current = ++revision.current;
    setState({ status: 'checking' });
    try {
      const identity = await fetchIdentity();
      if (current === revision.current) setState(identity ? { status: 'authenticated', identity } : { status: 'anonymous' });
    } catch {
      if (current === revision.current) setState({ status: 'error' });
    }
  }, []);
  useEffect(() => { void checkSession(); }, [checkSession]);
  const register = useCallback(async (firstName: string, lastName: string) => {
    const { identity, created } = await registerParticipant(firstName, lastName);
    // The API interceptor obtains a fresh CSRF token before the next mutation.
    revision.current++;
    setState({ status: 'authenticated', identity });
    return created;
  }, []);
  const login = useCallback(async (username: string, password: string) => {
    try {
      const identity = await loginAdministrator(username, password);
      revision.current++;
      setState({ status: 'authenticated', identity });
    } catch (error) {
      if (error instanceof ParticipantSessionError) await checkSession();
      throw error;
    }
  }, [checkSession]);
  const logout = useCallback(async () => {
    try { await logoutSession(); }
    catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error; }
    await realtime.stop();
    revision.current++;
    setState({ status: 'anonymous' });
  }, []);
  return <AuthContext.Provider value={{ state, checkSession, register, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
