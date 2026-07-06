/**
 * 가격을 한국 원화 형식으로 포맷 (예: 70,150)
 */
export function formatPrice(price: number): string {
  return price.toLocaleString('ko-KR');
}

/**
 * 등락률 포맷 (예: +1.23% / -0.45%)
 */
export function formatChangeRate(rate: number): string {
  const sign = rate >= 0 ? '+' : '';
  return `${sign}${rate.toFixed(2)}%`;
}

/**
 * 금액 포맷 (예: 1,234,567원)
 */
export function formatAmount(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`;
}

/**
 * 수익률 포맷 (예: +12.34%)
 */
export function formatProfitRate(rate: number): string {
  const sign = rate >= 0 ? '+' : '';
  return `${sign}${rate.toFixed(2)}%`;
}

/**
 * 날짜 포맷 (예: 2026.07.06 14:30)
 */
export function formatDate(isoString: string): string {
  const d = new Date(isoString);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}.${m}.${day} ${h}:${min}`;
}

/**
 * 거래량 단위 포맷 (예: 1.2만, 3.5억)
 */
export function formatVolume(volume: number): string {
  if (volume >= 100_000_000) {
    return `${(volume / 100_000_000).toFixed(1)}억`;
  }
  if (volume >= 10_000) {
    return `${(volume / 10_000).toFixed(1)}만`;
  }
  return volume.toLocaleString('ko-KR');
}
