import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../services/api';
import { realtime, type RealtimeEvent, type RealtimeStatus } from '../../services/realtime';
import { notification, type DrawState } from './homeState';
import { useAuth } from './auth';

type DrawLoad = { status: 'loading' | 'error'; data?: DrawState } | { status: 'ready'; data: DrawState };
export function useParticipantActivity() {
  const [draw, setDraw] = useState<DrawLoad>({ status: 'loading' });
  const [connection, setConnection] = useState<RealtimeStatus>('connecting');
  const [notice, setNotice] = useState<string | null>(null);
  const revision = useRef(0);
  const seen = useRef(new Set<string>());
  const { checkSession } = useAuth();
  const refresh = useCallback(async () => {
    const request = ++revision.current;
    try {
      const result = await api.get<DrawState>('/participants/me/draw');
      if (request === revision.current) setDraw({ status: 'ready', data: result.data });
    } catch (error) {
      if (request !== revision.current) return;
      if (error instanceof ApiError && error.status === 401) { void checkSession(); return; }
      setDraw(previous => ({ status: 'error', data: previous.data }));
    }
  }, [checkSession]);
  useEffect(() => {
    const requestRevision = revision;
    let active = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const recoverPrize = async () => {
      try {
        const result = await api.get<{ content: { prizeName: string }[] }>('/participants/me/prizes', { params: { page: 0, size: 1 } });
        const prize = result.data.content[0];
        if (active && prize?.prizeName) setNotice(previous => previous ?? `You won ${prize.prizeName}!`);
      } catch { /* Other activity remains usable if prize history is unavailable. */ }
    };
    const scheduleRefresh = () => { if (timer) clearTimeout(timer); timer = setTimeout(() => void refresh(), 200); };
    const onMessage = (body: string, isPrivate: boolean) => {
      try {
        const event = JSON.parse(body) as RealtimeEvent<unknown>;
        if (!event.eventId || seen.current.has(event.eventId)) return;
        seen.current.add(event.eventId);
        if (seen.current.size > 200) seen.current.delete(seen.current.values().next().value!);
        if (isPrivate) { const message = notification(event.type, event.payload); if (message) setNotice(message); }
        if (event.type === 'PRIZE_WON') void recoverPrize();
        if (event.type.startsWith('DRAW_') || event.type === 'PARTICIPANT_SELECTED' || event.type === 'PRIZE_WON') scheduleRefresh();
      } catch { /* Ignore malformed or unsupported messages. */ }
    };
    const unsubscribePrivate = realtime.subscribe('/user/queue/notifications', message => onMessage(message.body, true));
    const unsubscribeDraw = realtime.subscribe('/topic/draw', message => onMessage(message.body, false));
    const unsubscribeConnected = realtime.onConnected(() => { scheduleRefresh(); void recoverPrize(); });
    const unsubscribeStatus = realtime.onStatus(setConnection);
    realtime.start();
    void refresh();
    void recoverPrize();
    const interval = setInterval(() => { if (document.visibilityState === 'visible') void refresh(); }, 30000);
    return () => { active = false; requestRevision.current++; if (timer) clearTimeout(timer); clearInterval(interval); unsubscribePrivate(); unsubscribeDraw(); unsubscribeConnected(); unsubscribeStatus(); };
  }, [refresh]);
  return { draw, connection, notice, refresh };
}
