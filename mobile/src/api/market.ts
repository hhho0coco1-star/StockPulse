import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';
import type { StockSymbol, StockQuote, Candle, WatchlistItem } from '@/types/market';

export const marketApi = {
  async searchSymbols(market: 'KR' | 'US', query: string): Promise<StockSymbol[]> {
    const { data } = await apiClient.get<ApiResponse<StockSymbol[]>>('/market/symbols', {
      params: { market, query },
    });
    if (!data.success || !data.data) throw new Error('종목 검색 실패');
    return data.data;
  },

  async getQuote(symbol: string): Promise<StockQuote> {
    const { data } = await apiClient.get<ApiResponse<StockQuote>>(`/market/quote/${symbol}`);
    if (!data.success || !data.data) throw new Error('시세 조회 실패');
    return data.data;
  },

  async getQuotes(symbols: string[]): Promise<StockQuote[]> {
    const { data } = await apiClient.get<ApiResponse<StockQuote[]>>('/market/quotes', {
      params: { symbols: symbols.join(',') },
    });
    if (!data.success || !data.data) throw new Error('시세 조회 실패');
    return data.data;
  },

  async getCandles(
    symbol: string,
    interval: '1m' | '5m' | '1d',
    from?: string,
    to?: string,
  ): Promise<Candle[]> {
    const { data } = await apiClient.get<ApiResponse<Candle[]>>(
      `/market/candles/${symbol}`,
      { params: { interval, from, to } },
    );
    if (!data.success || !data.data) throw new Error('캔들 조회 실패');
    return data.data;
  },
};

export const watchlistApi = {
  async getWatchlist(): Promise<WatchlistItem[]> {
    const { data } = await apiClient.get<ApiResponse<WatchlistItem[]>>('/watchlist');
    if (!data.success || !data.data) throw new Error('워치리스트 조회 실패');
    return data.data;
  },

  async addToWatchlist(symbol: string): Promise<void> {
    await apiClient.post('/watchlist', { symbol });
  },

  async removeFromWatchlist(symbol: string): Promise<void> {
    await apiClient.delete(`/watchlist/${symbol}`);
  },
};
