import type { IMessage } from '@stomp/stompjs';
import { getStompClient } from './StompClient';
import { useWsStore } from '@/store/wsStore';

type MessageHandler<T> = (payload: T) => void;

function parseMessage<T>(msg: IMessage): T {
  return JSON.parse(msg.body) as T;
}

export function subscribeMarketTick<T>(
  symbol: string,
  handler: MessageHandler<T>,
): () => void {
  const key = `/topic/market/${symbol}`;
  const client = getStompClient();
  const sub = client.subscribe(key, (msg) => handler(parseMessage<T>(msg)));
  useWsStore.getState().addSubscription(key, sub);
  return () => useWsStore.getState().removeSubscription(key);
}

export function subscribeInsightUpdate<T>(
  symbol: string,
  handler: MessageHandler<T>,
): () => void {
  const key = `/topic/insight/${symbol}`;
  const client = getStompClient();
  const sub = client.subscribe(key, (msg) => handler(parseMessage<T>(msg)));
  useWsStore.getState().addSubscription(key, sub);
  return () => useWsStore.getState().removeSubscription(key);
}

export function subscribeChat<T>(
  symbol: string,
  handler: MessageHandler<T>,
): () => void {
  const key = `/topic/chat/${symbol}`;
  const client = getStompClient();
  const sub = client.subscribe(key, (msg) => handler(parseMessage<T>(msg)));
  useWsStore.getState().addSubscription(key, sub);
  return () => useWsStore.getState().removeSubscription(key);
}

export function subscribeOrderQueue<T>(handler: MessageHandler<T>): () => void {
  const key = '/user/queue/orders';
  const client = getStompClient();
  const sub = client.subscribe(key, (msg) => handler(parseMessage<T>(msg)));
  useWsStore.getState().addSubscription(key, sub);
  return () => useWsStore.getState().removeSubscription(key);
}

export function sendChatMessage(symbol: string, message: string): void {
  const client = getStompClient();
  client.publish({
    destination: `/app/chat/${symbol}`,
    body: JSON.stringify({ message }),
  });
}
