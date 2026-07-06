import React from 'react';
import { View, Text, TouchableOpacity, Switch, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import type { AlertRule } from '@/types/notification';

interface AlertRuleItemProps {
  rule: AlertRule;
  onToggle: (id: string, enabled: boolean) => void;
  onDelete: (id: string) => void;
}

const TYPE_LABELS: Record<string, string> = {
  TARGET_PRICE: '목표가',
  CHANGE_RATE: '급등락',
  NEWS: '뉴스',
};

export function AlertRuleItem({ rule, onToggle, onDelete }: AlertRuleItemProps): React.JSX.Element {
  const conditionText =
    rule.type === 'TARGET_PRICE'
      ? `${rule.condition.targetPrice?.toLocaleString()}원 도달`
      : rule.type === 'CHANGE_RATE'
        ? `±${rule.condition.changeRate}%`
        : '호재 뉴스';

  return (
    <View style={styles.item}>
      <View style={styles.left}>
        <View style={styles.typeBadge}>
          <Text style={styles.typeText}>{TYPE_LABELS[rule.type] ?? rule.type}</Text>
        </View>
        <Text style={styles.symbol}>{rule.name ?? rule.symbol}</Text>
        <Text style={styles.condition}>{conditionText}</Text>
      </View>
      <View style={styles.right}>
        <Switch
          value={rule.enabled}
          onValueChange={(v) => onToggle(rule.id, v)}
          trackColor={{ true: '#FF3B30', false: '#3A3A3C' }}
        />
        <TouchableOpacity onPress={() => onDelete(rule.id)} style={styles.deleteBtn}>
          <Ionicons name="trash-outline" size={18} color="#636366" />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
  },
  left: { flex: 1 },
  typeBadge: {
    backgroundColor: '#2C2C2E',
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
    marginBottom: 4,
  },
  typeText: { color: '#8E8E93', fontSize: 11 },
  symbol: { color: '#FFFFFF', fontSize: 14, fontWeight: '500' },
  condition: { color: '#8E8E93', fontSize: 12, marginTop: 2 },
  right: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  deleteBtn: { padding: 4 },
});
