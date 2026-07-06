import React, { useState, useEffect, useRef } from 'react';
import {
  View, Text, FlatList, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform, ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useLocalSearchParams } from 'expo-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { communityApi } from '@/api/community';
import { PostItem } from '@/components/PostItem';
import { ChatMessage } from '@/components/ChatMessage';
import { useWsStore } from '@/store/wsStore';
import { useAuthStore } from '@/store/authStore';
import { subscribeChat, sendChatMessage } from '@/websocket/subscriptions';
import type { Post, ChatMessage as ChatMessageType } from '@/types/community';

type Tab = 'posts' | 'chat';

export default function CommunitySymbolScreen(): React.JSX.Element {
  const { symbol } = useLocalSearchParams<{ symbol: string }>();
  const [activeTab, setActiveTab] = useState<Tab>('posts');
  const [chatMessages, setChatMessages] = useState<ChatMessageType[]>([]);
  const [chatInput, setChatInput] = useState('');
  const [postInput, setPostInput] = useState('');
  const scrollRef = useRef<ScrollView>(null);
  const queryClient = useQueryClient();
  const { isConnected } = useWsStore();
  const { user } = useAuthStore();

  const postsQuery = useQuery({
    queryKey: ['posts', symbol],
    queryFn: () => communityApi.getPosts(symbol, 0),
    enabled: !!symbol,
  });

  // 실시간 채팅 구독
  useEffect(() => {
    if (!isConnected || !symbol) return;
    const unsub = subscribeChat<ChatMessageType>(symbol, (msg) => {
      setChatMessages((prev) => [...prev, msg]);
      setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    });
    return unsub;
  }, [isConnected, symbol]);

  const createPostMutation = useMutation({
    mutationFn: (content: string) => communityApi.createPost(symbol, { content }),
    onSuccess: () => {
      setPostInput('');
      queryClient.invalidateQueries({ queryKey: ['posts', symbol] });
    },
  });

  const likeMutation = useMutation({
    mutationFn: (postId: string) => communityApi.toggleLike(postId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['posts', symbol] }),
  });

  const handleSendChat = () => {
    if (!chatInput.trim()) return;
    sendChatMessage(symbol, chatInput.trim());
    setChatInput('');
  };

  const posts = postsQuery.data?.content ?? [];

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>{symbol} 토론방</Text>

      {/* 탭 전환 */}
      <View style={styles.tabs}>
        {(['posts', 'chat'] as Tab[]).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tab, activeTab === tab && styles.tabActive]}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.tabText, activeTab === tab && { color: '#FFFFFF' }]}>
              {tab === 'posts' ? '게시글' : '실시간 채팅'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {activeTab === 'posts' ? (
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
          <FlatList
            data={posts}
            keyExtractor={(item: Post) => item.postId}
            renderItem={({ item }) => (
              <PostItem
                post={item}
                onLike={() => likeMutation.mutate(item.postId)}
              />
            )}
            ListEmptyComponent={<Text style={styles.empty}>첫 게시글을 작성해 보세요!</Text>}
          />
          <View style={styles.inputRow}>
            <TextInput
              style={styles.input}
              placeholder="게시글 작성..."
              placeholderTextColor="#636366"
              value={postInput}
              onChangeText={setPostInput}
              multiline
            />
            <TouchableOpacity
              style={styles.sendBtn}
              onPress={() => createPostMutation.mutate(postInput)}
              disabled={!postInput.trim()}
            >
              <Text style={styles.sendBtnText}>등록</Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      ) : (
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
          <ScrollView
            ref={scrollRef}
            style={styles.chatScroll}
            contentContainerStyle={{ paddingVertical: 8 }}
          >
            {chatMessages.map((msg) => (
              <ChatMessage key={msg.messageId} message={msg} isMe={msg.userId === user?.userId} />
            ))}
          </ScrollView>
          <View style={styles.inputRow}>
            <TextInput
              style={styles.input}
              placeholder="메시지 입력..."
              placeholderTextColor="#636366"
              value={chatInput}
              onChangeText={setChatInput}
              onSubmitEditing={handleSendChat}
              returnKeyType="send"
            />
            <TouchableOpacity style={styles.sendBtn} onPress={handleSendChat}>
              <Text style={styles.sendBtnText}>전송</Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0A0E1A' },
  title: { color: '#FFFFFF', fontSize: 18, fontWeight: '700', padding: 16, paddingBottom: 8 },
  tabs: { flexDirection: 'row', marginHorizontal: 16, marginBottom: 8, gap: 8 },
  tab: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#1C1C1E',
  },
  tabActive: { backgroundColor: '#3A3A3C' },
  tabText: { color: '#8E8E93', fontWeight: '600' },
  chatScroll: { flex: 1 },
  inputRow: {
    flexDirection: 'row',
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#2C2C2E',
  },
  input: {
    flex: 1,
    backgroundColor: '#1C1C1E',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
    color: '#FFFFFF',
    fontSize: 14,
    maxHeight: 80,
  },
  sendBtn: {
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    paddingHorizontal: 14,
    justifyContent: 'center',
  },
  sendBtnText: { color: '#FFFFFF', fontWeight: '600' },
  empty: { color: '#636366', textAlign: 'center', padding: 24 },
});
