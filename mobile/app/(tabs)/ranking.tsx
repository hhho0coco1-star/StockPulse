import React, { useState } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { rankingApi } from '@/api/trading';
import { RankingRow } from '@/components/RankingRow';
import type { RankingEntry } from '@/types/trading';

type Period = 'daily' | 'weekly' | 'all';

const PERIOD_LABELS: Record<Period, string> = {
  daily: '일간',
  weekly: '주간',
  all: '전체',
};

export default function RankingScreen(): React.JSX.Element {
  const [period, setPeriod] = useState<Period>('daily');

  const rankingQuery = useInfiniteQuery({
    queryKey: ['ranking', period],
    queryFn: ({ pageParam = 0 }) => rankingApi.getRanking(period, pageParam as number),
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    initialPageParam: 0,
  });

  const myRankQuery = useQuery({
    queryKey: ['ranking', 'me'],
    queryFn: rankingApi.getMyRanking,
  });

  const allEntries = rankingQuery.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>랭킹</Text>

      {/* 내 순위 강조 */}
      {myRankQuery.data && (
        <View style={styles.myRank}>
          <Text style={styles.myRankLabel}>내 순위</Text>
          <Text style={styles.myRankValue}>{myRankQuery.data.rank}위</Text>
          <Text style={styles.myRankRate}>
            {myRankQuery.data.profitLossRate >= 0 ? '+' : ''}
            {myRankQuery.data.profitLossRate.toFixed(2)}%
          </Text>
          <Text style={styles.myRankTotal}>
            전체 {myRankQuery.data.totalParticipants.toLocaleString()}명
          </Text>
        </View>
      )}

      {/* 기간 탭 */}
      <View style={styles.periodTabs}>
        {(Object.entries(PERIOD_LABELS) as [Period, string][]).map(([p, label]) => (
          <TouchableOpacity
            key={p}
            style={[styles.periodTab, period === p && styles.periodTabActive]}
            onPress={() => setPeriod(p)}
          >
            <Text style={[styles.periodTabText, period === p && { color: '#FFFFFF' }]}>
              {label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={allEntries}
        keyExtractor={(item: RankingEntry) => `${item.rank}-${item.userId}`}
        renderItem={({ item }) => <RankingRow entry={item} />}
        onEndReached={() => rankingQuery.fetchNextPage()}
        onEndReachedThreshold={0.3}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '700', padding: 16 },
  myRank: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1C2A3A',
    marginHorizontal: 16,
    borderRadius: 10,
    padding: 14,
    gap: 12,
    marginBottom: 12,
  },
  myRankLabel: { color: '#8E8E93', fontSize: 12 },
  myRankValue: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
  myRankRate: { color: '#FF3B30', fontSize: 15, fontWeight: '600', flex: 1 },
  myRankTotal: { color: '#636366', fontSize: 11 },
  periodTabs: {
    flexDirection: 'row',
    marginHorizontal: 16,
    marginBottom: 8,
    gap: 8,
  },
  periodTab: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#1C1C1E',
  },
  periodTabActive: { backgroundColor: '#FF3B30' },
  periodTabText: { color: '#8E8E93', fontWeight: '600' },
});
