import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { formatChangeRate } from '@/utils/format';

interface PriceTagProps {
  changeRate: number;
  size?: 'sm' | 'md';
}

// 국내 색 관례: 상승=빨강, 하락=파랑
const UP_COLOR = '#FF3B30';
const DOWN_COLOR = '#007AFF';
const NEUTRAL_COLOR = '#8E8E93';

export function PriceTag({ changeRate, size = 'md' }: PriceTagProps): React.JSX.Element {
  const color =
    changeRate > 0 ? UP_COLOR : changeRate < 0 ? DOWN_COLOR : NEUTRAL_COLOR;

  return (
    <View style={[styles.badge, { backgroundColor: `${color}22` }]}>
      <Text style={[styles.text, { color, fontSize: size === 'sm' ? 11 : 13 }]}>
        {formatChangeRate(changeRate)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  text: {
    fontWeight: '600',
  },
});
