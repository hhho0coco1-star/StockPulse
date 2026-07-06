import { create } from 'zustand';
import type { Client, StompSubscription } from '@stomp/stompjs';

interface WsState {
  client: Client | null;
  isConnected: boolean;
  subscriptions: Map<string, StompSubscription>;

  setClient: (client: Client) => void;
  setConnected: (connected: boolean) => void;
  addSubscription: (key: string, sub: StompSubscription) => void;
  removeSubscription: (key: string) => void;
  clearSubscriptions: () => void;
}

export const useWsStore = create<WsState>((set, get) => ({
  client: null,
  isConnected: false,
  subscriptions: new Map(),

  setClient: (client) => set({ client }),
  setConnected: (connected) => set({ isConnected: connected }),

  addSubscription: (key, sub) => {
    const subs = new Map(get().subscriptions);
    subs.set(key, sub);
    set({ subscriptions: subs });
  },

  removeSubscription: (key) => {
    const subs = new Map(get().subscriptions);
    const sub = subs.get(key);
    if (sub) {
      sub.unsubscribe();
      subs.delete(key);
    }
    set({ subscriptions: subs });
  },

  clearSubscriptions: () => {
    get().subscriptions.forEach((sub) => sub.unsubscribe());
    set({ subscriptions: new Map() });
  },
}));
