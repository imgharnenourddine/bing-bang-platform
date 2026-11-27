import { api } from '../../services/api';
import { fetchIdentity, type AdminIdentity } from '../participant/registrationApi';

export class ParticipantSessionError extends Error {}
export class InvalidAdminSessionError extends Error {}

/** Recheck immediately before login so an already authenticated participant is not intentionally replaced. */
export async function loginAdministrator(username: string, password: string): Promise<AdminIdentity> {
  const current = await fetchIdentity();
  if (current?.role === 'PARTICIPANT') throw new ParticipantSessionError();
  if (current?.role === 'ADMIN') return current;
  const response = await api.post<AdminIdentity>('/auth/admin/login', { username, password });
  if (response.data.role !== 'ADMIN' || !response.data.id || !response.data.username) throw new InvalidAdminSessionError();
  const verified = await fetchIdentity();
  if (!verified || verified.role !== 'ADMIN' || verified.id !== response.data.id) throw new InvalidAdminSessionError();
  return verified;
}
export async function logoutSession(): Promise<void> { await api.post('/auth/logout'); }
export function validateCredentials(username: string, password: string) {
  return { username: username.trim() ? '' : 'Enter your username.', password: password.trim() ? '' : 'Enter your password.' };
}
