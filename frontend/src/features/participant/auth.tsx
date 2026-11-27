import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { fetchIdentity, registerParticipant, type Identity } from './registrationApi';

type AuthState = { status: 'checking' | 'anonymous' | 'error' } | { status: 'authenticated'; identity: Identity };
interface AuthContextValue {
  state: AuthState;
  checkSession: () => Promise<void>;
  register: (firstName: string, lastName: string) => Promise<boolean>;
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
  return <AuthContext.Provider value={{ state, checkSession, register }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
