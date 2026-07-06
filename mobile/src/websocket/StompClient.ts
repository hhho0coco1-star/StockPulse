import { Client, StompConfig } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import Constants from 'expo-constants';
import { storage } from '@/utils/storage';
import { useWsStore } from '@/store/wsStore';

const WS_URL =
  (Constants.expoConfig?.extra?.wsUrl as string | undefined) ??
  'wss://api.stockpulse.example.com/ws';

let stompClientInstance: Client | null = null;

export function getStompClient(): Client {
  if (stompClientInstance && stompClientInstance.connected) {
    return stompClientInstance;
  }

  const config: StompConfig = {
    webSocketFactory: () => new SockJS(WS_URL.replace(/^wss?:\/\//, 'https://').replace('/ws', '/ws')),
    reconnectDelay: 5_000,
    heartbeatIncoming: 4_000,
    heartbeatOutgoing: 4_000,

    onConnect: () => {
      useWsStore.getState().setConnected(true);
    },

    onDisconnect: () => {
      useWsStore.getState().setConnected(false);
    },

    onStompError: (frame) => {
      console.error('[STOMP] Error:', frame.headers['message']);
      useWsStore.getState().setConnected(false);
    },

    beforeConnect: async () => {
      const token = await storage.getAccessToken();
      if (token && stompClientInstance) {
        stompClientInstance.connectHeaders = {
          Authorization: `Bearer ${token}`,
        };
      }
    },
  };

  stompClientInstance = new Client(config);
  useWsStore.getState().setClient(stompClientInstance);
  return stompClientInstance;
}

export async function connectStomp(): Promise<void> {
  const client = getStompClient();
  if (!client.connected) {
    client.activate();
  }
}

export function disconnectStomp(): void {
  if (stompClientInstance?.connected) {
    stompClientInstance.deactivate();
    stompClientInstance = null;
    useWsStore.getState().setConnected(false);
    useWsStore.getState().clearSubscriptions();
  }
}
