import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { formatProfitRate } from '@/utils/format';
import type { RankingEntry } from '@/types/trading';

interface RankingRowProps {
  entry: RankingEntry;
}

export function RankingRow({ entry }: RankingRowProps): React.JSX.Element {
  const isPositive = entry.profitLossRate >= 0;
  const rateColor = isPositive ? '#FF3B30' : '#007AFF';

  return (
    <View style={[styles.row, entry.isMe && styles.highlight]}>
      <Text style={[styles.rank, entry.rank <= 3 && styles.topRank]}>
        {entry.rank}
      </Text>
      <Text style={styles.nickname} numberOfLines={1}>
        {entry.nickname}
        {entry.isMe ? ' (나)' : ''}
      </Text>
      <Text style={[styles.rate, { color: rateColor }]}>
        {formatProfitRate(entry.profitLossRate)}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
    gap: 12,
  },
  highlight: { backgroundColor: '#1C2A3A' },
  rank: { color: '#8E8E93', fontSize: 14, width: 32, textAlign: 'center' },
  topRank: { color: '#FFD700', fontWeight: '700' },
  nickname: { flex: 1, color: '#FFFFFF', fontSize: 14 },
  rate: { fontSize: 15, fontWeight: '600' },
});
