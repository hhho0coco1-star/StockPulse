import React, { useEffect, useCallback } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity, RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { watchlistApi, marketApi } from '@/api/market';
import { insightApi } from '@/api/insight';
import { StockRow } from '@/components/StockRow';
import { InsightCard } from '@/components/InsightCard';
import { useWsStore } from '@/store/wsStore';
import { subscribeMarketTick } from '@/websocket/subscriptions';
import type { MarketTick, StockQuote } from '@/types/market';
import type { InsightSummary } from '@/types/insight';

export default function HomeScreen(): React.JSX.Element {
  const queryClient = useQueryClient();
  const { isConnected } = useWsStore();

  const watchlistQuery = useQuery({
    queryKey: ['watchlist'],
    queryFn: watchlistApi.getWatchlist,
  });

  const symbols = (watchlistQuery.data ?? []).map((w) => w.symbol);

  const quotesQuery = useQuery({
    queryKey: ['quotes', symbols],
    queryFn: () => marketApi.getQuotes(symbols),
    enabled: symbols.length > 0,
  });

  const insightsQuery = useQuery({
    queryKey: ['insights', 'strong', 'KR'],
    queryFn: () => insightApi.getStrongInsights('KR', 0),
  });

  // 워치리스트 각 종목 시세 실시간 구독
  useEffect(() => {
    if (!isConnected || symbols.length === 0) return;

    const unsubscribes = symbols.map((symbol) =>
      subscribeMarketTick<MarketTick>(symbol, (tick) => {
        queryClient.setQueryData<StockQuote[]>(['quotes', symbols], (prev) => {
          if (!prev) return prev;
          return prev.map((q) =>
            q.symbol === tick.symbol
              ? { ...q, price: tick.price, change: tick.change, changeRate: tick.changeRate }
              : q,
          );
        });
      }),
    );

    return () => unsubscribes.forEach((unsub) => unsub());
  }, [isConnected, symbols, queryClient]);

  const refresh = useCallback(() => {
    watchlistQuery.refetch();
    quotesQuery.refetch();
    insightsQuery.refetch();
  }, [watchlistQuery, quotesQuery, insightsQuery]);

  const quotes = quotesQuery.data ?? [];
  const insights = insightsQuery.data?.content ?? [];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>StockPulse</Text>
        <View style={styles.headerRight}>
          <View style={[styles.wsIndicator, { backgroundColor: isConnected ? '#30D158' : '#FF453A' }]} />
          <TouchableOpacity onPress={() => router.push('/alerts')}>
            <Ionicons name="notifications-outline" size={22} color="#FFFFFF" />
          </TouchableOpacity>
        </View>
      </View>

      <FlatList
        data={[]}
        keyExtractor={() => 'main'}
        renderItem={null}
        refreshControl={
          <RefreshControl
            refreshing={watchlistQuery.isFetching}
            onRefresh={refresh}
            tintColor="#FF3B30"
          />
        }
        ListHeaderComponent={
          <>
            <Text style={styles.sectionTitle}>내 워치리스트</Text>
            {quotes.map((q) => (
              <StockRow
                key={q.symbol}
                quote={q}
                onPress={() => router.push(`/stock/${q.symbol}`)}
              />
            ))}
            {quotes.length === 0 && !quotesQuery.isFetching && (
              <Text style={styles.empty}>관심 종목을 추가해 주세요</Text>
            )}

            <Text style={styles.sectionTitle}>오늘의 강세 인사이트</Text>
            {insights.map((ins: InsightSummary) => (
              <InsightCard key={ins.symbol} insight={ins} />
            ))}
          </>
        }
        showsVerticalScrollIndicator={false}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerTitle: { color: '#FFFFFF', fontSize: 20, fontWeight: '700' },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  wsIndicator: { width: 8, height: 8, borderRadius: 4 },
  sectionTitle: {
    color: '#8E8E93',
    fontSize: 13,
    fontWeight: '600',
    paddingHorizontal: 16,
    paddingTop: 20,
    paddingBottom: 8,
    textTransform: 'uppercase',
  },
  empty: { color: '#636366', textAlign: 'center', padding: 24 },
});
