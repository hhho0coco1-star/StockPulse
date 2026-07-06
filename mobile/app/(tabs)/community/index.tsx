import React, { useState } from 'react';
import { View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { useQuery } from '@tanstack/react-query';
import { marketApi } from '@/api/market';
import type { StockSymbol } from '@/types/market';

export default function CommunityIndexScreen(): React.JSX.Element {
  const [query, setQuery] = useState('');
  const [searched, setSearched] = useState(false);

  const searchQuery = useQuery({
    queryKey: ['symbols', 'KR', query],
    queryFn: () => marketApi.searchSymbols('KR', query),
    enabled: searched && query.trim().length > 0,
  });

  const handleSearch = () => {
    if (query.trim()) setSearched(true);
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>커뮤니티</Text>

      <View style={styles.searchRow}>
        <TextInput
          style={styles.input}
          placeholder="종목명 또는 코드 검색"
          placeholderTextColor="#636366"
          value={query}
          onChangeText={(t) => { setQuery(t); setSearched(false); }}
          onSubmitEditing={handleSearch}
          returnKeyType="search"
        />
        <TouchableOpacity style={styles.searchBtn} onPress={handleSearch}>
          <Text style={styles.searchBtnText}>검색</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={searchQuery.data ?? []}
        keyExtractor={(item: StockSymbol) => item.symbol}
        renderItem={({ item }) => (
          <TouchableOpacity
            style={styles.resultItem}
            onPress={() => router.push(`/(tabs)/community/${item.symbol}`)}
          >
            <Text style={styles.symbolName}>{item.name}</Text>
            <Text style={styles.symbolCode}>{item.symbol}</Text>
          </TouchableOpacity>
        )}
        ListEmptyComponent={
          searched ? (
            <Text style={styles.empty}>검색 결과가 없습니다</Text>
          ) : (
            <Text style={styles.hint}>종목을 검색해 토론방에 참여하세요</Text>
          )
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '700', padding: 16 },
  searchRow: { flexDirection: 'row', marginHorizontal: 16, marginBottom: 12, gap: 8 },
  input: {
    flex: 1,
    backgroundColor: '#1C1C1E',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: '#FFFFFF',
    fontSize: 15,
  },
  searchBtn: {
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    paddingHorizontal: 16,
    justifyContent: 'center',
  },
  searchBtnText: { color: '#FFFFFF', fontWeight: '600' },
  resultItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
  },
  symbolName: { color: '#FFFFFF', fontSize: 15 },
  symbolCode: { color: '#8E8E93', fontSize: 13 },
  empty: { color: '#636366', textAlign: 'center', padding: 24 },
  hint: { color: '#636366', textAlign: 'center', padding: 24 },
});
