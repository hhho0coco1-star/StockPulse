import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { PriceTag } from './PriceTag';
import { formatPrice } from '@/utils/format';
import type { StockQuote } from '@/types/market';

interface StockRowProps {
  quote: StockQuote;
  onPress?: () => void;
}

export function StockRow({ quote, onPress }: StockRowProps): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.row} onPress={onPress} activeOpacity={0.7}>
      <View style={styles.left}>
        <Text style={styles.name} numberOfLines={1}>
          {quote.name}
        </Text>
        <Text style={styles.symbol}>{quote.symbol}</Text>
      </View>
      <View style={styles.right}>
        <Text style={styles.price}>{formatPrice(quote.price)}</Text>
        <PriceTag changeRate={quote.changeRate} size="sm" />
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
  },
  left: { flex: 1, marginRight: 12 },
  name: { color: '#FFFFFF', fontSize: 15, fontWeight: '500' },
  symbol: { color: '#8E8E93', fontSize: 12, marginTop: 2 },
  right: { alignItems: 'flex-end' },
  price: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
});
