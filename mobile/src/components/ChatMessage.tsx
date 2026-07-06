import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { ChatMessage as ChatMessageType } from '@/types/community';

interface ChatMessageProps {
  message: ChatMessageType;
  isMe: boolean;
}

export function ChatMessage({ message, isMe }: ChatMessageProps): React.JSX.Element {
  return (
    <View style={[styles.wrapper, isMe ? styles.wrapperMe : styles.wrapperOther]}>
      {!isMe && <Text style={styles.nickname}>{message.nickname}</Text>}
      <View style={[styles.bubble, isMe ? styles.bubbleMe : styles.bubbleOther]}>
        <Text style={styles.text}>{message.message}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginVertical: 4, marginHorizontal: 12, maxWidth: '75%' },
  wrapperMe: { alignSelf: 'flex-end', alignItems: 'flex-end' },
  wrapperOther: { alignSelf: 'flex-start', alignItems: 'flex-start' },
  nickname: { color: '#8E8E93', fontSize: 11, marginBottom: 3 },
  bubble: { borderRadius: 12, paddingHorizontal: 12, paddingVertical: 8 },
  bubbleMe: { backgroundColor: '#007AFF' },
  bubbleOther: { backgroundColor: '#3A3A3C' },
  text: { color: '#FFFFFF', fontSize: 14, lineHeight: 19 },
});
