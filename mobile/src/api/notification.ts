import { apiClient } from './client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { AlertRule, AlertRuleRequest, DeviceToken, NotificationHistory } from '@/types/notification';

export const notificationApi = {
  async getAlerts(): Promise<AlertRule[]> {
    const { data } = await apiClient.get<ApiResponse<AlertRule[]>>('/alerts');
    if (!data.success || !data.data) throw new Error('알림 규칙 조회 실패');
    return data.data;
  },

  async createAlert(req: AlertRuleRequest): Promise<AlertRule> {
    const { data } = await apiClient.post<ApiResponse<AlertRule>>('/alerts', req);
    if (!data.success || !data.data) throw new Error('알림 규칙 생성 실패');
    return data.data;
  },

  async updateAlert(id: string, req: Partial<AlertRuleRequest>): Promise<AlertRule> {
    const { data } = await apiClient.put<ApiResponse<AlertRule>>(`/alerts/${id}`, req);
    if (!data.success || !data.data) throw new Error('알림 규칙 수정 실패');
    return data.data;
  },

  async deleteAlert(id: string): Promise<void> {
    await apiClient.delete(`/alerts/${id}`);
  },

  async registerDevice(req: DeviceToken): Promise<void> {
    await apiClient.post('/devices', req);
  },

  async unregisterDevice(token: string): Promise<void> {
    await apiClient.delete(`/devices/${token}`);
  },

  async getNotifications(page = 0): Promise<PageResponse<NotificationHistory>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<NotificationHistory>>>(
      '/notifications',
      { params: { page, size: 20 } },
    );
    if (!data.success || !data.data) throw new Error('알림 이력 조회 실패');
    return data.data;
  },
};
