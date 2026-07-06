/**
 * stockpulse:// 딥링크 URL을 expo-router 경로로 변환
 */
export function parseDeeplink(url: string): string {
  if (!url.startsWith('stockpulse://')) {
    return '/';
  }

  const path = url.replace('stockpulse://', '');
  const [segment, ...rest] = path.split('/');

  switch (segment) {
    case 'home':
      return '/(tabs)/';
    case 'stock':
      return rest[0] ? `/stock/${rest[0]}` : '/(tabs)/';
    case 'trading':
      return '/(tabs)/trading';
    case 'alerts':
      return '/alerts';
    default:
      return '/(tabs)/';
  }
}

/**
 * expo-router 경로를 stockpulse:// 딥링크 URL로 변환
 */
export function toDeeplink(path: string): string {
  if (path === '/(tabs)/') return 'stockpulse://home';
  if (path.startsWith('/stock/')) return `stockpulse://stock/${path.replace('/stock/', '')}`;
  if (path === '/(tabs)/trading') return 'stockpulse://trading';
  if (path === '/alerts') return 'stockpulse://alerts';
  return 'stockpulse://home';
}
