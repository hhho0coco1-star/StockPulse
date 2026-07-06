import { apiClient } from './client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type { InsightSummary, InsightFactors } from '@/types/insight';

export const insightApi = {
  async getInsight(symbol: string): Promise<InsightSummary> {
    const { data } = await apiClient.get<ApiResponse<InsightSummary>>(`/insights/${symbol}`);
    if (!data.success || !data.data) throw new Error('인사이트 조회 실패');
    return data.data;
  },

  async getInsightFactors(symbol: string): Promise<InsightFactors> {
    const { data } = await apiClient.get<ApiResponse<InsightFactors>>(
      `/insights/${symbol}/factors`,
    );
    if (!data.success || !data.data) throw new Error('인사이트 상세 조회 실패');
    return data.data;
  },

  async getStrongInsights(market: 'KR' | 'US', page = 0): Promise<PageResponse<InsightSummary>> {
    const { data } = await apiClient.get<ApiResponse<PageResponse<InsightSummary>>>(
      '/insights/strong',
      { params: { market, page, size: 20 } },
    );
    if (!data.success || !data.data) throw new Error('강세 인사이트 조회 실패');
    return data.data;
  },
};
