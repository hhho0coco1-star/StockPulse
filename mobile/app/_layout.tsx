import React, { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack, router, useSegments } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useAuthStore } from '@/store/authStore';
import { setupForegroundHandler, setupResponseHandler } from '@/notifications/handlers';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 2 },
    mutations: { retry: 0 },
  },
});

// AuthGuard: 인증 여부에 따라 라우트 보호
function AuthGuard(): null {
  const { isAuthenticated } = useAuthStore();
  const segments = useSegments();

  useEffect(() => {
    const inAuthGroup = segments[0] === '(auth)';
    if (!isAuthenticated && !inAuthGroup) {
      router.replace('/(auth)/login');
    } else if (isAuthenticated && inAuthGroup) {
      router.replace('/(tabs)/');
    }
  }, [isAuthenticated, segments]);

  return null;
}

export default function RootLayout(): React.JSX.Element {
  const { loadFromStorage } = useAuthStore();

  useEffect(() => {
    loadFromStorage();
    setupForegroundHandler();
    const cleanup = setupResponseHandler();
    return cleanup;
  }, [loadFromStorage]);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <QueryClientProvider client={queryClient}>
          <AuthGuard />
          <StatusBar style="light" />
          <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: '#0A0E1A' } }}>
            <Stack.Screen name="(auth)" />
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="stock/[symbol]" options={{ headerShown: true, title: '종목 상세', headerStyle: { backgroundColor: '#0A0E1A' }, headerTintColor: '#FFFFFF' }} />
            <Stack.Screen name="alerts" options={{ headerShown: true, title: '알림 설정', headerStyle: { backgroundColor: '#0A0E1A' }, headerTintColor: '#FFFFFF' }} />
          </Stack>
        </QueryClientProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
