export type InsightGrade = 'STRONG_BUY' | 'BUY' | 'NEUTRAL' | 'SELL' | 'STRONG_SELL';

export interface InsightSummary {
  symbol: string;
  name: string;
  score: number;       // 0~100
  grade: InsightGrade;
  summary: string;
  updatedAt: string;
}

export interface InsightFactor {
  name: 'momentum' | 'earnings' | 'valuation' | 'news';
  score: number;       // 0~100
  label: string;
  evidence: string[];  // 근거 문장 목록
}

export interface InsightFactors {
  symbol: string;
  totalScore: number;
  grade: InsightGrade;
  factors: InsightFactor[];
  disclaimer: string;
  updatedAt: string;
}

export interface InsightUpdate {
  symbol: string;
  score: number;
  grade: InsightGrade;
  updatedAt: string;
}
