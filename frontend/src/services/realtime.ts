import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { getCsrfToken } from './api';

export interface RealtimeEvent<T> {
  eventId: string;
  type: string;
  timestamp: string;
  version: number;
  payload: T;
}

type Subscription = { destination: string; callback: (message: IMessage) => void; active?: StompSubscription };
type ConnectionListener = () => void;

/** One connection per browser application instance. No STOMP SEND API is exposed. */
class RealtimeService {
  private client: Client | undefined;
  private running = false;
  private connecting = false;
  private generation = 0;
  private attempts = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private subscriptions = new Set<Subscription>();
  private listeners = new Set<ConnectionListener>();
  private readonly maxAttempts = 8;

  start(): void {
    if (this.running) return;
    this.running = true;
    this.attempts = 0;
    const generation = ++this.generation;
    void this.connect(generation);
  }

  async stop(): Promise<void> {
    this.running = false;
    this.generation++;
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = undefined;
    const client = this.client;
    this.client = undefined;
    this.subscriptions.forEach(subscription => { subscription.active = undefined; });
    if (client) await client.deactivate();
  }

  subscribe(destination: string, callback: (message: IMessage) => void): () => void {
    const subscription: Subscription = { destination, callback };
    this.subscriptions.add(subscription);
    if (this.client?.connected) subscription.active = this.client.subscribe(destination, callback);
    return () => {
      subscription.active?.unsubscribe();
      this.subscriptions.delete(subscription);
    };
  }

  /** Called after every successful connection, including reconnection; refresh REST state here. */
  onConnected(callback: ConnectionListener): () => void {
    this.listeners.add(callback);
    if (this.client?.connected) callback();
    return () => { this.listeners.delete(callback); };
  }

  private async connect(generation: number): Promise<void> {
    if (!this.running || this.connecting || generation !== this.generation) return;
    this.connecting = true;
    try {
      const csrf = await getCsrfToken();
      if (!this.running || generation !== this.generation) return;
      const scheme = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const client = new Client({
        brokerURL: `${scheme}//${window.location.host}/ws`,
        connectHeaders: { [csrf.headerName]: csrf.token },
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        reconnectDelay: 0,
        connectionTimeout: 10000,
      });
      this.client = client;
      client.onConnect = () => {
        if (!this.running || this.client !== client || generation !== this.generation) return;
        this.attempts = 0;
        this.subscriptions.forEach(subscription => {
          subscription.active = client.subscribe(subscription.destination, subscription.callback);
        });
        this.listeners.forEach(listener => listener());
      };
      client.onWebSocketClose = () => {
        if (this.client !== client) return;
        this.client = undefined;
        this.subscriptions.forEach(subscription => { subscription.active = undefined; });
        void client.deactivate();
        this.schedule(generation);
      };
      client.onStompError = () => {
        if (this.client !== client) return;
        this.client = undefined;
        void client.deactivate();
        this.schedule(generation);
      };
      client.activate();
    } catch {
      this.schedule(generation);
    } finally {
      this.connecting = false;
      // A stop/start while the token request is pending must not strand the new generation.
      if (this.running && generation !== this.generation && !this.client) void this.connect(this.generation);
    }
  }

  private schedule(generation: number): void {
    if (!this.running || generation !== this.generation || this.timer !== undefined || this.attempts >= this.maxAttempts) return;
    const delay = Math.min(1000 * 2 ** this.attempts++, 30000);
    this.timer = setTimeout(() => { this.timer = undefined; void this.connect(generation); }, delay);
  }
}

export const realtime = new RealtimeService();
