export type AlertType = 'TARGET_PRICE' | 'CHANGE_RATE' | 'NEWS';
export type Platform = 'ios' | 'android' | 'web';

export interface AlertCondition {
  targetPrice?: number;
  changeRate?: number;
  direction?: 'UP' | 'DOWN' | 'BOTH';
}

export interface AlertRule {
  id: string;
  symbol: string;
  name: string;
  type: AlertType;
  condition: AlertCondition;
  enabled: boolean;
  createdAt: string;
}

export interface AlertRuleRequest {
  symbol: string;
  type: AlertType;
  condition: AlertCondition;
}

export interface DeviceToken {
  token: string;
  platform: Platform;
}

export interface NotificationHistory {
  id: string;
  type: AlertType;
  symbol: string;
  title: string;
  body: string;
  deeplink: string;
  readAt?: string;
  createdAt: string;
}

export interface FcmData {
  type: AlertType;
  symbol: string;
  price?: string;
  changeRate?: string;
  newsId?: string;
  headline?: string;
  deeplink: string;
}
