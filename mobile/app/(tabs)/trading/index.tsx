import React, { useEffect } from 'react';
import { View, Text, FlatList, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { tradingApi } from '@/api/trading';
import { StockRow } from '@/components/StockRow';
import { OrderPanel } from '@/components/OrderPanel';
import { useWsStore } from '@/store/wsStore';
import { useUiStore } from '@/store/uiStore';
import { subscribeOrderQueue } from '@/websocket/subscriptions';
import { formatAmount, formatProfitRate } from '@/utils/format';
import type { OrderResult, PortfolioItem, OrderRequest } from '@/types/trading';

export default function TradingScreen(): React.JSX.Element {
  const queryClient = useQueryClient();
  const { isConnected } = useWsStore();
  const { showToast } = useUiStore();

  const accountQuery = useQuery({ queryKey: ['account'], queryFn: tradingApi.getAccount });
  const portfolioQuery = useQuery({ queryKey: ['portfolio'], queryFn: tradingApi.getPortfolio });
  const summaryQuery = useQuery({ queryKey: ['portfolioSummary'], queryFn: tradingApi.getPortfolioSummary });
  const myRankQuery = useQuery({ queryKey: ['ranking', 'me'], queryFn: () => import('@/api/trading').then((m) => m.rankingApi.getMyRanking()) });

  // 주문 Saga 결과 구독
  useEffect(() => {
    if (!isConnected) return;
    const unsub = subscribeOrderQueue<OrderResult>((result) => {
      if (result.status === 'COMPLETED') {
        showToast(`${result.symbol} 체결 완료 (${result.executedPrice?.toLocaleString()}원)`, 'success');
      } else {
        showToast(`주문 실패: ${result.errorMessage ?? '알 수 없는 오류'}`, 'error');
      }
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      queryClient.invalidateQueries({ queryKey: ['account'] });
      queryClient.invalidateQueries({ queryKey: ['portfolioSummary'] });
    });
    return unsub;
  }, [isConnected, showToast, queryClient]);

  const orderMutation = useMutation({
    mutationFn: (req: OrderRequest) => tradingApi.placeOrder(req),
    onSuccess: ({ orderId }) => {
      showToast(`주문 접수 완료 (ID: ${orderId.slice(0, 8)}...)`, 'info');
    },
    onError: (err: Error) => {
      showToast(err.message, 'error');
    },
  });

  const summary = summaryQuery.data;
  const account = accountQuery.data;
  const portfolio = portfolioQuery.data ?? [];

  // 포트폴리오 첫 번째 종목의 현재가를 OrderPanel에 기본값으로 사용
  const firstStock = portfolio[0];

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>모의투자</Text>
      <Text style={styles.disclaimer}>
        본 서비스는 투자자문이 아닌 공개 데이터 정보 제공입니다. 모든 거래는 가상입니다.
      </Text>

      <ScrollView showsVerticalScrollIndicator={false}>
        {/* KPI 카드 */}
        <View style={styles.kpiGrid}>
          <View style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>총 평가금액</Text>
            <Text style={styles.kpiValue}>{formatAmount(summary?.totalEvaluation ?? 0)}</Text>
          </View>
          <View style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>총 수익률</Text>
            <Text style={[
              styles.kpiValue,
              { color: (summary?.totalProfitLossRate ?? 0) >= 0 ? '#FF3B30' : '#007AFF' },
            ]}>
              {formatProfitRate(summary?.totalProfitLossRate ?? 0)}
            </Text>
          </View>
          <View style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>예수금</Text>
            <Text style={styles.kpiValue}>{formatAmount(account?.cash ?? 0)}</Text>
          </View>
          <View style={styles.kpiCard}>
            <Text style={styles.kpiLabel}>랭킹</Text>
            <Text style={styles.kpiValue}>{myRankQuery.data?.rank ?? '-'}위</Text>
          </View>
        </View>

        {/* 보유 종목 */}
        <Text style={styles.sectionTitle}>보유 종목</Text>
        {portfolio.map((item: PortfolioItem) => (
          <StockRow
            key={item.symbol}
            quote={{
              symbol: item.symbol,
              name: item.name,
              price: item.currentPrice,
              change: item.profitLoss,
              changeRate: item.profitLossRate,
              volume: 0,
              high: item.currentPrice,
              low: item.currentPrice,
              open: item.averagePrice,
              prevClose: item.averagePrice,
              updatedAt: new Date().toISOString(),
            }}
            onPress={() => router.push(`/stock/${item.symbol}`)}
          />
        ))}

        {/* 주문 패널 */}
        {firstStock && (
          <>
            <Text style={styles.sectionTitle}>주문</Text>
            <View style={{ paddingHorizontal: 16, paddingBottom: 20 }}>
              <OrderPanel
                symbol={firstStock.symbol}
                currentPrice={firstStock.currentPrice}
                onSubmit={(req) => orderMutation.mutate(req)}
                isLoading={orderMutation.isPending}
              />
            </View>
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '700', padding: 16, paddingBottom: 4 },
  disclaimer: {
    color: '#636366',
    fontSize: 11,
    paddingHorizontal: 16,
    paddingBottom: 12,
    lineHeight: 16,
  },
  kpiGrid: { flexDirection: 'row', flexWrap: 'wrap', paddingHorizontal: 12, gap: 8 },
  kpiCard: {
    flex: 1,
    minWidth: '45%',
    backgroundColor: '#1C1C1E',
    borderRadius: 10,
    padding: 14,
  },
  kpiLabel: { color: '#8E8E93', fontSize: 12 },
  kpiValue: { color: '#FFFFFF', fontSize: 18, fontWeight: '700', marginTop: 4 },
  sectionTitle: {
    color: '#8E8E93',
    fontSize: 13,
    fontWeight: '600',
    paddingHorizontal: 16,
    paddingTop: 20,
    paddingBottom: 8,
    textTransform: 'uppercase',
  },
});
