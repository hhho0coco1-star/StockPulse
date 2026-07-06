export type OrderSide = 'BUY' | 'SELL';
export type OrderType = 'MARKET' | 'LIMIT';
export type OrderStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface OrderRequest {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
  price?: number;
}

export interface Order {
  orderId: string;
  symbol: string;
  name: string;
  side: OrderSide;
  type: OrderType;
  quantity: number;
  price: number;
  status: OrderStatus;
  executedPrice?: number;
  executedQuantity?: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderResult {
  orderId: string;
  status: 'COMPLETED' | 'FAILED';
  symbol: string;
  executedPrice?: number;
  executedQuantity?: number;
  errorMessage?: string;
}

export interface Account {
  cash: number;
  reserved: number;
  total: number;
}

export interface PortfolioItem {
  symbol: string;
  name: string;
  quantity: number;
  averagePrice: number;
  currentPrice: number;
  evaluationAmount: number;
  profitLoss: number;
  profitLossRate: number;
}

export interface PortfolioSummary {
  totalEvaluation: number;
  totalProfitLoss: number;
  totalProfitLossRate: number;
  totalInvested: number;
}

export interface RankingEntry {
  rank: number;
  userId: string;
  nickname: string;
  profitLossRate: number;
  totalEvaluation: number;
  isMe: boolean;
}

export interface MyRanking {
  rank: number;
  profitLossRate: number;
  totalParticipants: number;
}
