import React, { useState } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useInfiniteQuery } from '@tanstack/react-query';
import { insightApi } from '@/api/insight';
import { InsightCard } from '@/components/InsightCard';
import type { InsightSummary } from '@/types/insight';

export default function InsightScreen(): React.JSX.Element {
  const [market, setMarket] = useState<'KR' | 'US'>('KR');

  const insightsQuery = useInfiniteQuery({
    queryKey: ['insights', 'strong', market],
    queryFn: ({ pageParam = 0 }) => insightApi.getStrongInsights(market, pageParam as number),
    getNextPageParam: (lastPage) =>
      lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined,
    initialPageParam: 0,
  });

  const allInsights = insightsQuery.data?.pages.flatMap((p) => p.content) ?? [];

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>인사이트</Text>

      <View style={styles.marketToggle}>
        {(['KR', 'US'] as const).map((m) => (
          <TouchableOpacity
            key={m}
            style={[styles.marketBtn, market === m && styles.marketBtnActive]}
            onPress={() => setMarket(m)}
          >
            <Text style={[styles.marketBtnText, market === m && { color: '#FFFFFF' }]}>
              {m === 'KR' ? '국내' : '해외'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <FlatList
        data={allInsights}
        keyExtractor={(item: InsightSummary) => item.symbol}
        renderItem={({ item }) => (
          <TouchableOpacity onPress={() => router.push(`/stock/${item.symbol}`)}>
            <InsightCard insight={item} />
          </TouchableOpacity>
        )}
        onEndReached={() => insightsQuery.fetchNextPage()}
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
  marketToggle: { flexDirection: 'row', marginHorizontal: 16, marginBottom: 8, gap: 8 },
  marketBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#1C1C1E',
  },
  marketBtnActive: { backgroundColor: '#FF3B30' },
  marketBtnText: { color: '#8E8E93', fontWeight: '600' },
});
