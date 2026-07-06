import * as Notifications from 'expo-notifications';
import { router } from 'expo-router';
import { parseDeeplink } from '@/utils/deeplink';
import type { FcmData } from '@/types/notification';

/**
 * 포그라운드 알림 핸들러 — 인앱 배너 표시
 * app/_layout.tsx에서 앱 최초 마운트 시 한 번 호출
 */
export function setupForegroundHandler(): void {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowAlert: true,
      shouldPlaySound: true,
      shouldSetBadge: true,
    }),
  });
}

/**
 * 알림 탭(사용자 클릭) 응답 핸들러 — 딥링크 이동
 * app/_layout.tsx에서 구독 등록 후 cleanup 함수 반환
 */
export function setupResponseHandler(): () => void {
  const subscription = Notifications.addNotificationResponseReceivedListener((response) => {
    const fcmData = response.notification.request.content.data as Partial<FcmData>;
    const deeplink = fcmData?.deeplink;
    if (deeplink) {
      const path = parseDeeplink(deeplink);
      router.push(path as Parameters<typeof router.push>[0]);
    }
  });

  return () => subscription.remove();
}
