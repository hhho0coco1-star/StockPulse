import React, { useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform,
} from 'react-native';
import { router } from 'expo-router';
import { useMutation } from '@tanstack/react-query';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/authStore';
import { connectStomp } from '@/websocket/StompClient';
import { setupPushNotifications } from '@/notifications/setup';

export default function SignupScreen(): React.JSX.Element {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const { setTokens, setUser } = useAuthStore();

  const signupMutation = useMutation({
    mutationFn: () => authApi.signup({ email, password, nickname }),
    onSuccess: async (data) => {
      await setTokens(data.accessToken, data.refreshToken);
      setUser({ userId: data.userId, email, nickname: data.nickname });
      await connectStomp();
      await setupPushNotifications();
      router.replace('/(tabs)/');
    },
    onError: (err: Error) => {
      alert(err.message);
    },
  });

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      style={styles.container}
    >
      <Text style={styles.title}>회원가입</Text>

      <TextInput
        style={styles.input}
        placeholder="이메일"
        placeholderTextColor="#636366"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <TextInput
        style={styles.input}
        placeholder="닉네임"
        placeholderTextColor="#636366"
        value={nickname}
        onChangeText={setNickname}
      />
      <TextInput
        style={styles.input}
        placeholder="비밀번호"
        placeholderTextColor="#636366"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />

      <TouchableOpacity
        style={styles.btn}
        onPress={() => signupMutation.mutate()}
        disabled={signupMutation.isPending}
      >
        <Text style={styles.btnText}>
          {signupMutation.isPending ? '처리 중...' : '가입하기'}
        </Text>
      </TouchableOpacity>

      <TouchableOpacity onPress={() => router.back()} style={styles.link}>
        <Text style={styles.linkText}>이미 계정이 있으신가요? 로그인</Text>
      </TouchableOpacity>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0A0E1A',
    padding: 24,
    justifyContent: 'center',
    gap: 12,
  },
  title: { color: '#FFFFFF', fontSize: 28, fontWeight: '700', marginBottom: 16 },
  input: {
    backgroundColor: '#1C1C1E',
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 14,
    color: '#FFFFFF',
    fontSize: 16,
  },
  btn: {
    backgroundColor: '#FF3B30',
    borderRadius: 10,
    paddingVertical: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  btnText: { color: '#FFFFFF', fontWeight: '700', fontSize: 16 },
  link: { alignItems: 'center', marginTop: 8 },
  linkText: { color: '#8E8E93', fontSize: 14 },
});
