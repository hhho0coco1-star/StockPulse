import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { formatDate } from '@/utils/format';
import type { Post } from '@/types/community';

interface PostItemProps {
  post: Post;
  onPress?: () => void;
  onLike?: () => void;
}

export function PostItem({ post, onPress, onLike }: PostItemProps): React.JSX.Element {
  return (
    <TouchableOpacity style={styles.item} onPress={onPress} activeOpacity={0.7}>
      <View style={styles.meta}>
        <Text style={styles.author}>{post.authorNickname}</Text>
        <Text style={styles.date}>{formatDate(post.createdAt)}</Text>
      </View>
      <Text style={styles.content} numberOfLines={3}>
        {post.content}
      </Text>
      <View style={styles.actions}>
        <TouchableOpacity onPress={onLike} style={styles.action}>
          <Ionicons
            name={post.liked ? 'heart' : 'heart-outline'}
            size={15}
            color={post.liked ? '#FF3B30' : '#8E8E93'}
          />
          <Text style={styles.actionText}>{post.likeCount}</Text>
        </TouchableOpacity>
        <View style={styles.action}>
          <Ionicons name="chatbubble-outline" size={15} color="#8E8E93" />
          <Text style={styles.actionText}>{post.commentCount}</Text>
        </View>
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  item: {
    padding: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#2C2C2E',
  },
  meta: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 },
  author: { color: '#FFFFFF', fontWeight: '500', fontSize: 13 },
  date: { color: '#8E8E93', fontSize: 11 },
  content: { color: '#EBEBF5CC', fontSize: 14, lineHeight: 20 },
  actions: { flexDirection: 'row', gap: 16, marginTop: 8 },
  action: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  actionText: { color: '#8E8E93', fontSize: 12 },
});
