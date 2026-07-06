import * as Notifications from 'expo-notifications';
import * as Device from 'expo-device';
import { Alert, Platform } from 'react-native';
import { notificationApi } from '@/api/notification';

/**
 * FCM 권한 요청 + 토큰 등록 플로우
 * 앱 최초 실행(또는 로그인 후) 한 번 호출
 */
export async function setupPushNotifications(): Promise<void> {
  if (!Device.isDevice) {
    console.log('[FCM] 실기기가 아님 — 푸시 토큰 등록 건너뜀');
    return;
  }

  const { status: existingStatus } = await Notifications.getPermissionsAsync();
  let finalStatus = existingStatus;

  if (existingStatus !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    finalStatus = status;
  }

  if (finalStatus !== 'granted') {
    Alert.alert(
      '알림 권한 필요',
      '목표가 도달·급등락 알림을 받으려면 알림 권한이 필요합니다. 설정 > StockPulse > 알림에서 허용해 주세요.',
      [{ text: '확인' }],
    );
    return;
  }

  try {
    const tokenData = await Notifications.getDevicePushTokenAsync();
    const platform = Platform.OS === 'ios' ? 'ios' : 'android';
    await notificationApi.registerDevice({ token: tokenData.data, platform });

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('default', {
        name: 'StockPulse 알림',
        importance: Notifications.AndroidImportance.MAX,
        vibrationPattern: [0, 250, 250, 250],
        lightColor: '#FF3B30',
      });
    }
  } catch (err) {
    console.error('[FCM] 토큰 등록 실패:', err);
  }
}
