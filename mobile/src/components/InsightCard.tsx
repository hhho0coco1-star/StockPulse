import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, LayoutAnimation } from 'react-native';
import type { InsightSummary, InsightFactors, InsightFactor } from '@/types/insight';

interface InsightCardProps {
  insight: InsightSummary;
  factors?: InsightFactors;
}

const FACTOR_LABELS: Record<InsightFactor['name'], string> = {
  momentum: '모멘텀',
  earnings: '실적',
  valuation: '밸류에이션',
  news: '뉴스',
};

const GRADE_COLORS: Record<string, string> = {
  STRONG_BUY: '#FF3B30',
  BUY: '#FF9500',
  NEUTRAL: '#8E8E93',
  SELL: '#5AC8FA',
  STRONG_SELL: '#007AFF',
};

export function InsightCard({ insight, factors }: InsightCardProps): React.JSX.Element {
  const [expanded, setExpanded] = useState(false);

  const toggle = () => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpanded((v) => !v);
  };

  const gradeColor = GRADE_COLORS[insight.grade] ?? '#8E8E93';

  return (
    <View style={styles.card}>
      <TouchableOpacity onPress={toggle} activeOpacity={0.8} style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.name}>{insight.name}</Text>
          <Text style={styles.symbol}>{insight.symbol}</Text>
        </View>
        <View style={styles.headerRight}>
          <View style={[styles.scoreBadge, { backgroundColor: `${gradeColor}22` }]}>
            <Text style={[styles.scoreText, { color: gradeColor }]}>{insight.score}</Text>
          </View>
          <Text style={[styles.grade, { color: gradeColor }]}>{insight.grade}</Text>
        </View>
      </TouchableOpacity>

      <Text style={styles.summary} numberOfLines={expanded ? undefined : 2}>
        {insight.summary}
      </Text>

      {expanded && factors && (
        <View style={styles.factors}>
          {factors.factors.map((f) => (
            <View key={f.name} style={styles.factorRow}>
              <Text style={styles.factorName}>{FACTOR_LABELS[f.name]}</Text>
              <View style={styles.barContainer}>
                <View style={[styles.bar, { width: `${f.score}%`, backgroundColor: gradeColor }]} />
              </View>
              <Text style={styles.factorScore}>{f.score}</Text>
            </View>
          ))}
          {factors.disclaimer ? (
            <Text style={styles.disclaimer}>{factors.disclaimer}</Text>
          ) : null}
        </View>
      )}

      <TouchableOpacity onPress={toggle} style={styles.toggleBtn}>
        <Text style={styles.toggleText}>{expanded ? '접기 ▲' : '상세 보기 ▼'}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#1C1C1E',
    borderRadius: 12,
    marginHorizontal: 16,
    marginVertical: 6,
    padding: 14,
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  headerLeft: { flex: 1 },
  name: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
  symbol: { color: '#8E8E93', fontSize: 12, marginTop: 2 },
  headerRight: { alignItems: 'flex-end' },
  scoreBadge: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 8, marginBottom: 4 },
  scoreText: { fontWeight: '700', fontSize: 16 },
  grade: { fontSize: 11, fontWeight: '600' },
  summary: { color: '#EBEBF5CC', fontSize: 13, lineHeight: 19 },
  factors: { marginTop: 10, gap: 8 },
  factorRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  factorName: { color: '#8E8E93', fontSize: 12, width: 60 },
  barContainer: {
    flex: 1,
    height: 6,
    backgroundColor: '#3A3A3C',
    borderRadius: 3,
    overflow: 'hidden',
  },
  bar: { height: '100%', borderRadius: 3 },
  factorScore: { color: '#FFFFFF', fontSize: 12, width: 28, textAlign: 'right' },
  disclaimer: { color: '#636366', fontSize: 10, marginTop: 8, lineHeight: 14 },
  toggleBtn: { marginTop: 8, alignItems: 'center' },
  toggleText: { color: '#636366', fontSize: 12 },
});
