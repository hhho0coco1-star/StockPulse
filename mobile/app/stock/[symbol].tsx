import React from 'react';
import { useLocalSearchParams } from 'expo-router';
import { StockDetailScreen } from '@/screens/StockDetailScreen';

export default function StockDetailRoute(): React.JSX.Element {
  const { symbol } = useLocalSearchParams<{ symbol: string }>();
  return <StockDetailScreen symbol={symbol} />;
}
