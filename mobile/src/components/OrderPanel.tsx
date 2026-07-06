import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, StyleSheet } from 'react-native';
import type { OrderSide, OrderType } from '@/types/trading';

interface OrderPanelProps {
  symbol: string;
  currentPrice: number;
  onSubmit: (params: {
    symbol: string;
    side: OrderSide;
    type: OrderType;
    quantity: number;
    price?: number;
  }) => void;
  isLoading?: boolean;
}

export function OrderPanel({
  symbol,
  currentPrice,
  onSubmit,
  isLoading = false,
}: OrderPanelProps): React.JSX.Element {
  const [side, setSide] = useState<OrderSide>('BUY');
  const [orderType, setOrderType] = useState<OrderType>('MARKET');
  const [quantity, setQuantity] = useState('');
  const [limitPrice, setLimitPrice] = useState(String(currentPrice));

  const handleSubmit = () => {
    const qty = parseInt(quantity, 10);
    if (!qty || qty <= 0) return;
    onSubmit({
      symbol,
      side,
      type: orderType,
      quantity: qty,
      price: orderType === 'LIMIT' ? parseFloat(limitPrice) : undefined,
    });
  };

  return (
    <View style={styles.panel}>
      {/* 매수/매도 토글 */}
      <View style={styles.sideToggle}>
        <TouchableOpacity
          style={[styles.sideBtn, side === 'BUY' && styles.buyActive]}
          onPress={() => setSide('BUY')}
        >
          <Text style={[styles.sideBtnText, side === 'BUY' && { color: '#FFFFFF' }]}>매수</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.sideBtn, side === 'SELL' && styles.sellActive]}
          onPress={() => setSide('SELL')}
        >
          <Text style={[styles.sideBtnText, side === 'SELL' && { color: '#FFFFFF' }]}>매도</Text>
        </TouchableOpacity>
      </View>

      {/* 주문 유형 */}
      <View style={styles.typeToggle}>
        {(['MARKET', 'LIMIT'] as OrderType[]).map((t) => (
          <TouchableOpacity
            key={t}
            style={[styles.typeBtn, orderType === t && styles.typeActive]}
            onPress={() => setOrderType(t)}
          >
            <Text style={[styles.typeBtnText, orderType === t && { color: '#FFFFFF' }]}>
              {t === 'MARKET' ? '시장가' : '지정가'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* 지정가 입력 */}
      {orderType === 'LIMIT' && (
        <View style={styles.inputRow}>
          <Text style={styles.label}>가격</Text>
          <TextInput
            style={styles.input}
            value={limitPrice}
            onChangeText={setLimitPrice}
            keyboardType="numeric"
            placeholder="가격 입력"
            placeholderTextColor="#636366"
          />
        </View>
      )}

      {/* 수량 입력 */}
      <View style={styles.inputRow}>
        <Text style={styles.label}>수량</Text>
        <TextInput
          style={styles.input}
          value={quantity}
          onChangeText={setQuantity}
          keyboardType="numeric"
          placeholder="수량 입력"
          placeholderTextColor="#636366"
        />
      </View>

      <TouchableOpacity
        style={[styles.submitBtn, side === 'BUY' ? styles.buyBtn : styles.sellBtn]}
        onPress={handleSubmit}
        disabled={isLoading}
      >
        <Text style={styles.submitBtnText}>
          {isLoading ? '처리 중...' : side === 'BUY' ? '매수 주문' : '매도 주문'}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { backgroundColor: '#1C1C1E', borderRadius: 12, padding: 14, gap: 12 },
  sideToggle: { flexDirection: 'row', gap: 8 },
  sideBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#2C2C2E',
  },
  buyActive: { backgroundColor: '#FF3B30' },
  sellActive: { backgroundColor: '#007AFF' },
  sideBtnText: { color: '#8E8E93', fontWeight: '600' },
  typeToggle: { flexDirection: 'row', gap: 8 },
  typeBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 6,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#3A3A3C',
  },
  typeActive: { backgroundColor: '#3A3A3C' },
  typeBtnText: { color: '#8E8E93', fontSize: 13 },
  inputRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  label: { color: '#8E8E93', width: 40, fontSize: 13 },
  input: {
    flex: 1,
    backgroundColor: '#2C2C2E',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: '#FFFFFF',
    fontSize: 15,
  },
  submitBtn: { paddingVertical: 14, borderRadius: 10, alignItems: 'center' },
  buyBtn: { backgroundColor: '#FF3B30' },
  sellBtn: { backgroundColor: '#007AFF' },
  submitBtnText: { color: '#FFFFFF', fontWeight: '700', fontSize: 16 },
});
