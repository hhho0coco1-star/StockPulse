import React, { useState, useEffect } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity, Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { LineChart } from 'react-native-chart-kit';
import { marketApi, watchlistApi } from '@/api/market';
import { insightApi } from '@/api/insight';
import { InsightCard } from '@/components/InsightCard';
import { PriceTag } from '@/components/PriceTag';
import { useWsStore } from '@/store/wsStore';
import { subscribeMarketTick, subscribeInsightUpdate } from '@/websocket/subscriptions';
import { formatPrice } from '@/utils/format';
import type { MarketTick } from '@/types/market';
import type { InsightUpdate } from '@/types/insight';

const SCREEN_WIDTH = Dimensions.get('window').width;

type DetailTab = 'chart' | 'insight' | 'community';

interface StockDetailScreenProps {
  symbol: string;
}

export function StockDetailScreen({ symbol }: StockDetailScreenProps): React.JSX.Element {
  const [activeTab, setActiveTab] = useState<DetailTab>('chart');
  const queryClient = useQueryClient();
  const { isConnected } = useWsStore();

  const quoteQuery = useQuery({
    queryKey: ['quote', symbol],
    queryFn: () => marketApi.getQuote(symbol),
  });

  const candlesQuery = useQuery({
    queryKey: ['candles', symbol, '1d'],
    queryFn: () => marketApi.getCandles(symbol, '1d'),
    enabled: activeTab === 'chart',
  });

  const factorsQuery = useQuery({
    queryKey: ['insightFactors', symbol],
    queryFn: () => insightApi.getInsightFactors(symbol),
    enabled: activeTab === 'insight',
  });

  const insightQuery = useQuery({
    queryKey: ['insight', symbol],
    queryFn: () => insightApi.getInsight(symbol),
    enabled: activeTab === 'insight',
  });

  // 실시간 시세 구독
  useEffect(() => {
    if (!isConnected) return;
    const unsubMarket = subscribeMarketTick<MarketTick>(symbol, (tick) => {
      queryClient.setQueryData(['quote', symbol], (prev: typeof quoteQuery.data) => {
        if (!prev) return prev;
        return { ...prev, price: tick.price, change: tick.change, changeRate: tick.changeRate };
      });
    });
    const unsubInsight = subscribeInsightUpdate<InsightUpdate>(symbol, () => {
      queryClient.invalidateQueries({ queryKey: ['insightFactors', symbol] });
      queryClient.invalidateQueries({ queryKey: ['insight', symbol] });
    });
    return () => { unsubMarket(); unsubInsight(); };
  }, [isConnected, symbol, queryClient]);

  const quote = quoteQuery.data;
  const candles = candlesQuery.data ?? [];
  const chartData = candles.slice(-30).map((c) => c.close);

  return (
    <SafeAreaView style={styles.container}>
      {/* 가격 헤더 */}
      {quote && (
        <View style={styles.priceHeader}>
          <View>
            <Text style={styles.stockName}>{quote.name}</Text>
            <Text style={styles.price}>{formatPrice(quote.price)}</Text>
          </View>
          <PriceTag changeRate={quote.changeRate} size="md" />
        </View>
      )}

      {/* 탭 바 */}
      <View style={styles.tabBar}>
        {(['chart', 'insight', 'community'] as DetailTab[]).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tabItem, activeTab === tab && styles.tabItemActive]}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.tabText, activeTab === tab && { color: '#FF3B30' }]}>
              {tab === 'chart' ? '차트' : tab === 'insight' ? '인사이트' : '토론'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {activeTab === 'chart' && chartData.length > 1 && (
          <LineChart
            data={{
              labels: [],
              datasets: [{ data: chartData }],
            }}
            width={SCREEN_WIDTH - 32}
            height={200}
            withDots={false}
            withInnerLines={false}
            chartConfig={{
              backgroundGradientFrom: '#1C1C1E',
              backgroundGradientTo: '#1C1C1E',
              color: (opacity = 1) => `rgba(255, 59, 48, ${opacity})`,
              labelColor: () => '#8E8E93',
            }}
            bezier
            style={styles.chart}
          />
        )}

        {activeTab === 'insight' && insightQuery.data && (
          <InsightCard insight={insightQuery.data} factors={factorsQuery.data} />
        )}

        {activeTab === 'community' && (
          <TouchableOpacity
            style={styles.communityLink}
            onPress={() => router.push(`/(tabs)/community/${symbol}`)}
          >
            <Text style={styles.communityLinkText}>{symbol} 토론방 참여하기 →</Text>
          </TouchableOpacity>
        )}
      </ScrollView>

      {/* 하단 고정 — 모의 매수/매도 */}
      <View style={styles.bottomBar}>
        <TouchableOpacity
          style={[styles.tradeBtn, styles.buyBtn]}
          onPress={() => router.push({ pathname: '/(tabs)/trading/order', params: { symbol, side: 'BUY' } })}
        >
          <Text style={styles.tradeBtnText}>모의 매수</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.tradeBtn, styles.sellBtn]}
          onPress={() => router.push({ pathname: '/(tabs)/trading/order', params: { symbol, side: 'SELL' } })}
        >
          <Text style={styles.tradeBtnText}>모의 매도</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  priceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  stockName: { color: '#8E8E93', fontSize: 14 },
  price: { color: '#FFFFFF', fontSize: 28, fontWeight: '700' },
  tabBar: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#2C2C2E' },
  tabItem: { flex: 1, paddingVertical: 12, alignItems: 'center' },
  tabItemActive: { borderBottomWidth: 2, borderBottomColor: '#FF3B30' },
  tabText: { color: '#8E8E93', fontWeight: '600' },
  content: { flex: 1 },
  chart: { marginHorizontal: 16, marginTop: 12, borderRadius: 8 },
  communityLink: {
    margin: 16,
    padding: 16,
    backgroundColor: '#1C1C1E',
    borderRadius: 10,
    alignItems: 'center',
  },
  communityLinkText: { color: '#FF3B30', fontSize: 16, fontWeight: '600' },
  bottomBar: {
    flexDirection: 'row',
    padding: 12,
    gap: 12,
    borderTopWidth: 1,
    borderTopColor: '#2C2C2E',
  },
  tradeBtn: { flex: 1, paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  buyBtn: { backgroundColor: '#FF3B30' },
  sellBtn: { backgroundColor: '#007AFF' },
  tradeBtnText: { color: '#FFFFFF', fontWeight: '700', fontSize: 15 },
});
