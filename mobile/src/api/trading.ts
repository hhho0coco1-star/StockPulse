import { apiClient } from './client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  OrderRequest,
  Order,
  Account,
  PortfolioItem,
  PortfolioSummary,
} from '@/types/trading';

export const tradingApi = {
  async placeOrder(req: OrderRequest): Promise<{ orderId: string }> {
    const { data } = await apiClient.post<ApiResponse<{ orderId: string }>>('/orders', req);
    if (!data.success || !data.data) throw new Error(data.error?.message ?? '주문 실패');
    return data.data;
  },

  async getOrders(status?: string, page = 0): Promise<PageResponse<Order>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<Order>>>('/orders', {
      params: { status, page, size: 20 },
    });
    if (!data.success || !data.data) throw new Error('주문 내역 조회 실패');
    return data.data;
  },

  async getOrder(orderId: string): Promise<Order> {
    const { data } = await apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`);
    if (!data.success || !data.data) throw new Error('주문 상세 조회 실패');
    return data.data;
  },

  async cancelOrder(orderId: string): Promise<void> {
    await apiClient.delete(`/orders/${orderId}`);
  },

  async getAccount(): Promise<Account> {
    const { data } = await apiClient.get<ApiResponse<Account>>('/account');
    if (!data.success || !data.data) throw new Error('계좌 조회 실패');
    return data.data;
  },

  async resetAccount(): Promise<void> {
    await apiClient.post('/account/reset');
  },

  async getPortfolio(): Promise<PortfolioItem[]> {
    const { data } = await apiClient.get<ApiResponse<PortfolioItem[]>>('/portfolio');
    if (!data.success || !data.data) throw new Error('포트폴리오 조회 실패');
    return data.data;
  },

  async getPortfolioSummary(): Promise<PortfolioSummary> {
    const { data } = await apiClient.get<ApiResponse<PortfolioSummary>>('/portfolio/summary');
    if (!data.success || !data.data) throw new Error('포트폴리오 요약 조회 실패');
    return data.data;
  },
};

export const rankingApi = {
  async getRanking(
    period: 'daily' | 'weekly' | 'all',
    page = 0,
  ): Promise<PageResponse<import('@/types/trading').RankingEntry>> {
    const { data } = await apiClient.get<
      ApiResponse<PageResponse<import('@/types/trading').RankingEntry>>
    >('/ranking', { params: { period, page, size: 20 } });
    if (!data.success || !data.data) throw new Error('랭킹 조회 실패');
    return data.data;
  },

  async getMyRanking(): Promise<import('@/types/trading').MyRanking> {
    const { data } = await apiClient.get<ApiResponse<import('@/types/trading').MyRanking>>(
      '/ranking/me',
    );
    if (!data.success || !data.data) throw new Error('내 순위 조회 실패');
    return data.data;
  },
};
