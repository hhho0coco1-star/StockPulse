import React, { useState } from 'react';
import {
  View, Text, FlatList, StyleSheet, TouchableOpacity, TextInput, Modal,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/api/notification';
import { AlertRuleItem } from '@/components/AlertRuleItem';
import type { AlertRuleRequest, AlertType } from '@/types/notification';

export function AlertSettingsScreen(): React.JSX.Element {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [formSymbol, setFormSymbol] = useState('');
  const [formType, setFormType] = useState<AlertType>('TARGET_PRICE');
  const [formValue, setFormValue] = useState('');

  const alertsQuery = useQuery({
    queryKey: ['alerts'],
    queryFn: notificationApi.getAlerts,
  });

  const notificationsQuery = useQuery({
    queryKey: ['notifications'],
    queryFn: () => notificationApi.getNotifications(0),
  });

  const createMutation = useMutation({
    mutationFn: (req: AlertRuleRequest) => notificationApi.createAlert(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      setShowForm(false);
      setFormSymbol('');
      setFormValue('');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      notificationApi.updateAlert(id, { enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });

  const deleteMutation = useMutation({
    mutationFn: notificationApi.deleteAlert,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });

  const handleCreate = () => {
    if (!formSymbol.trim() || !formValue.trim()) return;
    const req: AlertRuleRequest = {
      symbol: formSymbol.trim().toUpperCase(),
      type: formType,
      condition:
        formType === 'TARGET_PRICE'
          ? { targetPrice: parseFloat(formValue) }
          : formType === 'CHANGE_RATE'
            ? { changeRate: parseFloat(formValue) }
            : {},
    };
    createMutation.mutate(req);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>알림 설정</Text>
        <TouchableOpacity style={styles.addBtn} onPress={() => setShowForm(true)}>
          <Text style={styles.addBtnText}>+ 규칙 추가</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>알림 규칙</Text>
      <FlatList
        data={alertsQuery.data ?? []}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <AlertRuleItem
            rule={item}
            onToggle={(id, enabled) => updateMutation.mutate({ id, enabled })}
            onDelete={(id) => deleteMutation.mutate(id)}
          />
        )}
        ListEmptyComponent={
          <Text style={styles.empty}>알림 규칙이 없습니다</Text>
        }
        style={styles.list}
      />

      <Text style={styles.sectionTitle}>받은 알림 이력</Text>
      <FlatList
        data={notificationsQuery.data?.content ?? []}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <View style={styles.notifItem}>
            <Text style={styles.notifTitle}>{item.title}</Text>
            <Text style={styles.notifBody}>{item.body}</Text>
            <Text style={styles.notifDate}>{item.createdAt}</Text>
          </View>
        )}
        ListEmptyComponent={<Text style={styles.empty}>받은 알림이 없습니다</Text>}
        style={styles.list}
      />

      {/* 규칙 생성 모달 */}
      <Modal visible={showForm} transparent animationType="slide">
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>알림 규칙 추가</Text>

            <TextInput
              style={styles.input}
              placeholder="종목 코드 (예: 005930)"
              placeholderTextColor="#636366"
              value={formSymbol}
              onChangeText={setFormSymbol}
              autoCapitalize="characters"
            />

            <View style={styles.typeRow}>
              {(['TARGET_PRICE', 'CHANGE_RATE', 'NEWS'] as AlertType[]).map((t) => (
                <TouchableOpacity
                  key={t}
                  style={[styles.typeBtn, formType === t && styles.typeBtnActive]}
                  onPress={() => setFormType(t)}
                >
                  <Text style={styles.typeBtnText}>
                    {t === 'TARGET_PRICE' ? '목표가' : t === 'CHANGE_RATE' ? '급등락' : '뉴스'}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {formType !== 'NEWS' && (
              <TextInput
                style={styles.input}
                placeholder={formType === 'TARGET_PRICE' ? '목표가 (원)' : '등락률 (%)'}
                placeholderTextColor="#636366"
                value={formValue}
                onChangeText={setFormValue}
                keyboardType="numeric"
              />
            )}

            <View style={styles.modalBtns}>
              <TouchableOpacity style={styles.cancelBtn} onPress={() => setShowForm(false)}>
                <Text style={styles.cancelBtnText}>취소</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.confirmBtn} onPress={handleCreate}>
                <Text style={styles.confirmBtnText}>추가</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
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
  title: { color: '#FFFFFF', fontSize: 20, fontWeight: '700' },
  addBtn: { backgroundColor: '#FF3B30', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 8 },
  addBtnText: { color: '#FFFFFF', fontWeight: '600', fontSize: 13 },
  sectionTitle: {
    color: '#8E8E93',
    fontSize: 12,
    fontWeight: '600',
    paddingHorizontal: 16,
    paddingVertical: 8,
    textTransform: 'uppercase',
  },
  list: { flexGrow: 0 },
  empty: { color: '#636366', textAlign: 'center', padding: 16 },
  notifItem: {
    padding: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
  },
  notifTitle: { color: '#FFFFFF', fontWeight: '500', fontSize: 14 },
  notifBody: { color: '#8E8E93', fontSize: 13, marginTop: 2 },
  notifDate: { color: '#636366', fontSize: 11, marginTop: 4 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  modalContent: {
    backgroundColor: '#1C1C1E',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    gap: 12,
  },
  modalTitle: { color: '#FFFFFF', fontSize: 18, fontWeight: '700' },
  input: {
    backgroundColor: '#2C2C2E',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    color: '#FFFFFF',
    fontSize: 15,
  },
  typeRow: { flexDirection: 'row', gap: 8 },
  typeBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#2C2C2E',
  },
  typeBtnActive: { backgroundColor: '#FF3B30' },
  typeBtnText: { color: '#FFFFFF', fontSize: 13 },
  modalBtns: { flexDirection: 'row', gap: 8, marginTop: 4 },
  cancelBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    backgroundColor: '#2C2C2E',
  },
  cancelBtnText: { color: '#8E8E93', fontWeight: '600' },
  confirmBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
    backgroundColor: '#FF3B30',
  },
  confirmBtnText: { color: '#FFFFFF', fontWeight: '700' },
});
