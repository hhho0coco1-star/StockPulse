import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams, router } from 'expo-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { marketApi } from '@/api/market';
import { tradingApi } from '@/api/trading';
import { OrderPanel } from '@/components/OrderPanel';
import { useUiStore } from '@/store/uiStore';
import type { OrderRequest } from '@/types/trading';

export default function OrderScreen(): React.JSX.Element {
  const { symbol } = useLocalSearchParams<{ symbol: string }>();
  const queryClient = useQueryClient();
  const { showToast } = useUiStore();

  const quoteQuery = useQuery({
    queryKey: ['quote', symbol],
    queryFn: () => marketApi.getQuote(symbol),
    enabled: !!symbol,
  });

  const orderMutation = useMutation({
    mutationFn: (req: OrderRequest) => tradingApi.placeOrder(req),
    onSuccess: ({ orderId }) => {
      showToast(`주문 접수 완료 (ID: ${orderId.slice(0, 8)}...)`, 'info');
      queryClient.invalidateQueries({ queryKey: ['portfolio'] });
      router.back();
    },
    onError: (err: Error) => {
      showToast(err.message, 'error');
    },
  });

  if (!symbol) return <View />;

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>주문 — {quoteQuery.data?.name ?? symbol}</Text>
      <ScrollView contentContainerStyle={styles.content}>
        {quoteQuery.data && (
          <OrderPanel
            symbol={symbol}
            currentPrice={quoteQuery.data.price}
            onSubmit={(req) => orderMutation.mutate(req)}
            isLoading={orderMutation.isPending}
          />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  title: { color: '#FFFFFF', fontSize: 18, fontWeight: '700', padding: 16 },
  content: { padding: 16 },
});
