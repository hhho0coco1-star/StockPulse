export interface StockSymbol {
  symbol: string;
  name: string;
  market: 'KR' | 'US';
}

export interface StockQuote {
  symbol: string;
  name: string;
  price: number;
  change: number;
  changeRate: number;
  volume: number;
  marketCap?: number;
  high: number;
  low: number;
  open: number;
  prevClose: number;
  updatedAt: string;
}

export interface Candle {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface MarketTick {
  symbol: string;
  price: number;
  change: number;
  changeRate: number;
  volume: number;
  timestamp: string;
}

export interface WatchlistItem {
  symbol: string;
  name: string;
  addedAt: string;
}
