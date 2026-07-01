// 테스트용 종목 풀 (캐시 분산 목적)
export const SYMBOLS = [
  '005930', // 삼성전자
  '000660', // SK하이닉스
  '035420', // NAVER
  '005380', // 현대차
  '051910', // LG화학
  '006400', // 삼성SDI
  '035720', // 카카오
  '207940', // 삼성바이오로직스
  '068270', // 셀트리온
  '000270', // 기아
];

export function randomSymbol() {
  return SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)];
}
