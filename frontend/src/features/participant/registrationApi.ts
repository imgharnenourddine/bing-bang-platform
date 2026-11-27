import { api, ApiError } from '../../services/api';

export interface ParticipantIdentity {
  id: string;
  participantNumber: number;
  firstName: string;
  lastName: string;
  role: 'PARTICIPANT';
}
export interface AdminIdentity { id: string; username: string; role: 'ADMIN' }
export type Identity = ParticipantIdentity | AdminIdentity;

export async function fetchIdentity(): Promise<Identity | null> {
  try {
    const response = await api.get<Identity>('/auth/me');
    if (response.data.role !== 'ADMIN' && response.data.role !== 'PARTICIPANT') throw new Error('Unexpected identity');
    return response.data;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
}

export async function registerParticipant(firstName: string, lastName: string): Promise<ParticipantIdentity> {
  const response = await api.post<ParticipantIdentity>('/participants/register', { firstName, lastName });
  if (response.data.role !== 'PARTICIPANT' || !response.data.id || !Number.isInteger(response.data.participantNumber)) {
    throw new Error('Unexpected registration response');
  }
  return response.data;
}

/** Prevents concurrent POSTs even before React has rendered the disabled state. */
export function createSubmissionGate() {
  let active = false;
  return {
    start(): boolean { if (active) return false; active = true; return true; },
    finish(): void { active = false; },
  };
}
