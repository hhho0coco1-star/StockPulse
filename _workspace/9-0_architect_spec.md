# Phase 9 — React Native 앱 구현 스펙

> Expo SDK 52 (React Native 0.76) 기반. TypeScript strict 모드. 상태관리: React Query(서버 상태) + Zustand(클라이언트 상태).

---

## 1. DoD (완료 기준)

검증 가능한 항목 7개:

1. **시세판 실시간 갱신**: 앱 실행 후 워치리스트 화면에서 `/topic/market/{symbol}` WebSocket 구독이 연결되고, 시세 수신 시 가격·등락률이 즉시 갱신된다 (화면 녹화로 확인).
2. **모의 주문 Saga 완료 통지**: 매수 주문 제출 후 202 응답을 받고, `/user/queue/orders` 구독으로 체결 완료(또는 실패) 알림이 앱 화면에 노출된다.
3. **FCM 포그라운드/백그라운드 수신**: 앱이 포그라운드 상태일 때 인앱 알림 배너가 표시되고, 백그라운드·종료 상태에서도 시스템 푸시가 수신된다.
4. **딥링크 이동**: 푸시 알림의 `deeplink: stockpulse://stock/{symbol}` 클릭 시 해당 종목 상세 화면으로 자동 이동한다.
5. **EAS Build 성공**: `eas build --platform all --profile preview` 명령이 오류 없이 완료되고, Expo 채널에서 QR코드로 앱 설치가 가능하다.
6. **인사이트 카드 렌더링**: `/insights/{symbol}/factors` 응답의 4축(모멘텀·실적·밸류에이션·뉴스) 점수와 근거가 카드 UI에 정상 표시된다.
7. **커뮤니티 실시간 채팅**: `/app/chat/{symbol}` 전송 후 `/topic/chat/{symbol}` 구독으로 내 메시지가 즉시 화면에 나타난다.

---

## 2. 기술 스택 (패키지명 + 버전)

### 코어 프레임워크
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `expo` | `~52.0.0` | SDK 기반, Managed Workflow |
| `react-native` | `0.76.x` | (Expo SDK 52 포함) |
| `react` | `18.3.x` | |
| `typescript` | `~5.3.0` | strict 모드 |
| `expo-router` | `~4.0.0` | Expo Router v3 기반 파일시스템 라우팅 |

### 상태관리 / 데이터 페칭
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `@tanstack/react-query` | `^5.0.0` | 서버 상태 캐싱·동기화 |
| `zustand` | `^4.5.0` | 클라이언트 전역 상태 (인증·WebSocket·UI) |
| `axios` | `^1.6.0` | REST API 호출 |

### 실시간 / WebSocket
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `@stomp/stompjs` | `^7.0.0` | STOMP over WebSocket |
| `sockjs-client` | `^1.6.0` | SockJS transport |

### 알림 / 푸시
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `expo-notifications` | `~0.28.0` | FCM 푸시 수신·처리·스케줄 |
| `expo-device` | `~6.0.0` | 실기기 여부 확인 (푸시 토큰 취득 조건) |

### 네비게이션 / UI
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `expo-linking` | `~6.3.0` | 딥링크 처리 |
| `react-native-safe-area-context` | `4.12.x` | Safe area insets |
| `react-native-screens` | `~4.0.0` | 네이티브 스크린 최적화 |
| `@react-navigation/bottom-tabs` | `^6.6.0` | 하단 5탭 바 |
| `react-native-gesture-handler` | `~2.20.0` | 제스처 처리 |
| `react-native-reanimated` | `~3.16.0` | 애니메이션 |

### 기타
| 패키지 | 버전 | 용도 |
|--------|------|------|
| `expo-secure-store` | `~13.0.0` | JWT 토큰 암호화 저장 |
| `expo-constants` | `~16.0.0` | app.json 환경변수 |
| `@expo/vector-icons` | `^14.0.0` | 아이콘 |
| `react-native-chart-kit` | `^6.12.0` | 간단 포트폴리오 수익률 차트 |

---

## 3. 프로젝트 구조 (app/ 전체 디렉토리 트리)

```
StockPulse/
└── mobile/                          # React Native 앱 루트
    ├── app.json                     # Expo 앱 설정
    ├── eas.json                     # EAS Build 프로파일
    ├── tsconfig.json                # strict: true
    ├── babel.config.js
    ├── app/                         # Expo Router 파일시스템 라우팅
    │   ├── _layout.tsx              # RootLayout: QueryClientProvider, AuthGuard
    │   ├── (auth)/                  # 인증 그룹 (비로그인 공개)
    │   │   ├── _layout.tsx
    │   │   ├── login.tsx            # 로그인 화면
    │   │   └── signup.tsx           # 회원가입 화면
    │   └── (tabs)/                  # 하단 탭 그룹 (로그인 필요)
    │       ├── _layout.tsx          # BottomTabNavigator (홈·인사이트·투자·커뮤니티·랭킹)
    │       ├── index.tsx            # 홈 = 메인 대시보드 (워치리스트 + 강세 인사이트)
    │       ├── insight.tsx          # 인사이트 탭 (강세 종목 리스트)
    │       ├── trading/
    │       │   ├── index.tsx        # 모의투자 대시보드 (포트폴리오 + 주문 패널)
    │       │   └── order.tsx        # 주문 상세 입력 화면
    │       ├── community/
    │       │   ├── index.tsx        # 커뮤니티 탭 (종목 검색 후 토론방 진입)
    │       │   └── [symbol].tsx     # 종목별 토론방 + 채팅
    │       └── ranking.tsx          # 랭킹 화면
    ├── src/
    │   ├── api/                     # API 계층
    │   │   ├── client.ts            # Axios 인스턴스 (BaseURL, 인터셉터)
    │   │   ├── auth.ts              # /auth/* 엔드포인트
    │   │   ├── market.ts            # /market/* 엔드포인트
    │   │   ├── insight.ts           # /insights/* 엔드포인트
    │   │   ├── trading.ts           # /orders, /account, /portfolio 엔드포인트
    │   │   ├── community.ts         # /community/* 엔드포인트
    │   │   ├── ranking.ts           # /ranking/* 엔드포인트
    │   │   ├── notification.ts      # /alerts, /devices, /notifications 엔드포인트
    │   │   └── watchlist.ts         # /watchlist 엔드포인트
    │   ├── hooks/                   # React Query + 커스텀 훅
    │   │   ├── useWatchlist.ts
    │   │   ├── useInsight.ts
    │   │   ├── useTrading.ts
    │   │   ├── useCommunity.ts
    │   │   ├── useRanking.ts
    │   │   └── useAlerts.ts
    │   ├── store/                   # Zustand 클라이언트 상태
    │   │   ├── authStore.ts         # 토큰, userId, 로그인 여부
    │   │   ├── wsStore.ts           # WebSocket 연결 상태·Client 인스턴스
    │   │   └── uiStore.ts           # 로딩·토스트·모달 플래그
    │   ├── websocket/
    │   │   ├── StompClient.ts       # STOMP 클라이언트 싱글톤 (연결·재연결 로직)
    │   │   └── subscriptions.ts     # 구독 헬퍼 함수
    │   ├── notifications/
    │   │   ├── setup.ts             # 권한 요청 + 토큰 등록 흐름
    │   │   └── handlers.ts          # 포그라운드·백그라운드·응답 핸들러
    │   ├── components/              # 재사용 UI 컴포넌트
    │   │   ├── StockRow.tsx         # 종목 1행 (이름·코드·가격·등락률)
    │   │   ├── InsightCard.tsx      # 전망 카드 (4축 점수 + 펼치기)
    │   │   ├── OrderPanel.tsx       # 매수/매도 입력 패널
    │   │   ├── ChatMessage.tsx      # 채팅 메시지 버블
    │   │   ├── PostItem.tsx         # 게시글 리스트 아이템
    │   │   ├── RankingRow.tsx       # 랭킹 1행
    │   │   ├── AlertRuleItem.tsx    # 알림 규칙 1행
    │   │   └── PriceTag.tsx         # 상승=빨강·하락=파랑 가격 배지
    │   ├── screens/                 # 화면 단위 스크린 컴포넌트
    │   │   ├── StockDetailScreen.tsx   # 종목 상세 (차트·인사이트·뉴스·토론 탭)
    │   │   └── AlertSettingsScreen.tsx # 알림 설정 화면
    │   ├── types/                   # TypeScript 타입 정의
    │   │   ├── api.ts               # API 응답 타입
    │   │   ├── market.ts
    │   │   ├── insight.ts
    │   │   ├── trading.ts
    │   │   └── notification.ts
    │   └── utils/
    │       ├── storage.ts           # expo-secure-store 래퍼 (JWT 저장/읽기)
    │       ├── format.ts            # 가격·수익률 포맷터
    │       └── deeplink.ts          # 딥링크 URL 파싱
    └── assets/
        ├── icon.png
        ├── splash.png
        └── adaptive-icon.png
```

---

## 4. 화면별 스펙

### 4.1 메인 대시보드 (`app/(tabs)/index.tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | `/(tabs)/` |
| 주요 컴포넌트 | `StockRow` × n (워치리스트), `InsightCard` × n (강세 인사이트) |
| API 호출 | `GET /watchlist`, `GET /market/quotes?symbols=...`, `GET /insights/strong?market=KR` |
| 실시간 | STOMP 구독: `/topic/market/{symbol}` — 수신 시 React Query cache 업데이트 (stale-while-revalidate) |
| 상태 | React Query: `watchlistQuery`, `quotesQuery`, `strongInsightsQuery` / Zustand: wsStore 연결 여부 |
| 색 규칙 | 상승=빨강(`#FF3B30`), 하락=파랑(`#007AFF`) — `PriceTag` 컴포넌트 공통 적용 |
| 진입점 | 홈 탭 아이콘 클릭 또는 딥링크 `stockpulse://home` |

### 4.2 종목 상세 (`src/screens/StockDetailScreen.tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | `stockpulse://stock/{symbol}` 딥링크 → `expo-router` push |
| 주요 컴포넌트 | 상단: 가격 헤더 + `react-native-chart-kit` 라인 차트; 탭 뷰: 차트·인사이트·뉴스·토론 |
| API 호출 | `GET /market/quote/{symbol}`, `GET /market/candles/{symbol}?interval=1d`, `GET /insights/{symbol}/factors` |
| 실시간 | STOMP: `/topic/market/{symbol}` (가격 갱신), `/topic/insight/{symbol}` (전망 카드 갱신) |
| 상태 | `InsightCard` 펼침/접힘 로컬 상태(`useState`), React Query: `quoteQuery`, `candlesQuery`, `insightFactorsQuery` |
| 하단 고정 | **모의 매수** / **모의 매도** 버튼 → `/(tabs)/trading/order` 화면으로 symbol 파라미터 전달 |

### 4.3 모의투자 (`app/(tabs)/trading/index.tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | `/(tabs)/trading` |
| 주요 컴포넌트 | KPI 카드(총평가·수익률·예수금·랭킹), 보유종목 `StockRow` 리스트, `OrderPanel` |
| API 호출 | `GET /portfolio`, `GET /portfolio/summary`, `GET /account`, `GET /ranking/me`, `POST /orders` |
| 실시간 | STOMP: `/user/queue/orders` — Saga 완료·실패 통지 수신 후 `uiStore` 토스트 표시 + `portfolioQuery.invalidate()` |
| 상태 | React Query mutation: `useOrderMutation` (낙관적 업데이트 없음, Saga 비동기라 202 후 대기) / Zustand: `uiStore.toast` |
| 주문 흐름 | `POST /orders` → 202 Accepted (orderId 반환) → STOMP `/user/queue/orders`에서 `COMPLETED`\|`FAILED` 수신 → 토스트 |

### 4.4 커뮤니티 (`app/(tabs)/community/[symbol].tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | `/(tabs)/community/[symbol]` (예: `/community/005930`) |
| 주요 컴포넌트 | 게시글 `FlatList` (`PostItem`), 댓글 `FlatList`, 실시간 채팅 `ScrollView` (`ChatMessage`) |
| API 호출 | `GET /community/{symbol}/posts?page=0`, `POST /community/{symbol}/posts`, `POST /community/posts/{postId}/comments`, `POST /community/posts/{postId}/like` |
| 실시간 | STOMP 구독: `/topic/chat/{symbol}` (채팅 수신 → `chatMessages` 배열 append) / 전송: `/app/chat/{symbol}` |
| 상태 | React Query: `postsQuery`, `commentsQuery` / Zustand `wsStore` 통해 STOMP send | 로컬: `chatMessages: ChatMessage[]` (useState) |
| 탭 분리 | 상단 탭: **게시글** / **채팅** — `useState`로 전환 |

### 4.5 랭킹 (`app/(tabs)/ranking.tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | `/(tabs)/ranking` |
| 주요 컴포넌트 | 기간 탭(일간·주간·전체) `SegmentedControl`, `RankingRow` FlatList, 내 순위 강조 배지 |
| API 호출 | `GET /ranking?period=daily&page=0`, `GET /ranking/me` |
| 상태 | React Query: `rankingQuery(period)`, `myRankQuery` / 로컬: `selectedPeriod: 'daily'|'weekly'|'all'` |
| 페이지네이션 | FlatList `onEndReached` → `fetchNextPage` (React Query infinite query) |

### 4.6 알림 설정 (`src/screens/AlertSettingsScreen.tsx`)

| 항목 | 내용 |
|------|------|
| 경로 | 홈 화면 상단 우측 벨 아이콘 → Modal 또는 Stack push |
| 주요 컴포넌트 | `AlertRuleItem` FlatList, 규칙 생성 폼(종목·유형·조건 값), 받은 알림 이력 리스트 |
| API 호출 | `GET /alerts`, `POST /alerts`, `PUT /alerts/{id}`, `DELETE /alerts/{id}`, `GET /notifications?page=` |
| FCM 토큰 등록 | 화면 첫 진입 시 `POST /devices` (토큰+플랫폼) 호출 — `notifications/setup.ts` 함수 |
| 상태 | React Query: `alertsQuery`, `notificationsQuery` / 로컬: 폼 상태 `useForm` 또는 `useState` |

---

## 5. API 계약 (백엔드 연동 엔드포인트 목록)

Base URL: `https://{host}/api/v1`  
인증 헤더: `Authorization: Bearer {accessToken}`

### 5.1 Auth
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| POST | `/auth/login` | 로그인 |
| POST | `/auth/signup` | 회원가입 |
| POST | `/auth/refresh` | 토큰 재발급 (Axios 인터셉터 자동) |
| POST | `/auth/logout` | 설정 |
| GET | `/auth/oauth2/{provider}` | 소셜 로그인 (google/kakao) |
| GET | `/users/me` | 프로필 |

### 5.2 워치리스트
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| GET | `/watchlist` | 메인 대시보드 |
| POST | `/watchlist` | 종목 상세 (관심 추가) |
| DELETE | `/watchlist/{symbol}` | 종목 상세 (관심 삭제) |

### 5.3 시세
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| GET | `/market/symbols?market=KR&query=` | 종목 검색 |
| GET | `/market/quote/{symbol}` | 종목 상세 헤더 |
| GET | `/market/quotes?symbols=` | 메인 대시보드 |
| GET | `/market/candles/{symbol}?interval=1d&from=&to=` | 종목 상세 차트 |

### 5.4 인사이트
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| GET | `/insights/{symbol}` | 종목 상세 (요약 카드) |
| GET | `/insights/{symbol}/factors` | 종목 상세 (4축 상세) |
| GET | `/insights/strong?market=KR&page=` | 메인 대시보드, 인사이트 탭 |

### 5.5 모의투자
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| POST | `/orders` | 주문 패널 |
| GET | `/orders?status=&page=` | 주문 내역 |
| GET | `/orders/{orderId}` | 주문 상세 |
| DELETE | `/orders/{orderId}` | 미체결 취소 |
| GET | `/account` | 모의투자 대시보드 |
| POST | `/account/reset` | 설정 |
| GET | `/portfolio` | 모의투자 대시보드 |
| GET | `/portfolio/summary` | 모의투자 KPI 카드 |
| GET | `/ranking?period=daily|weekly|all&page=` | 랭킹 화면 |
| GET | `/ranking/me` | 랭킹 화면 (내 순위) |

### 5.6 커뮤니티
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| GET | `/community/{symbol}/posts?page=` | 커뮤니티 |
| POST | `/community/{symbol}/posts` | 커뮤니티 |
| GET | `/community/posts/{postId}` | 게시글 상세 |
| DELETE | `/community/posts/{postId}` | 게시글 삭제 |
| GET | `/community/posts/{postId}/comments` | 댓글 목록 |
| POST | `/community/posts/{postId}/comments` | 댓글 작성 |
| POST | `/community/posts/{postId}/like` | 좋아요 토글 |

### 5.7 알림
| 메서드 | 경로 | 사용 화면 |
|--------|------|----------|
| GET | `/alerts` | 알림 설정 |
| POST | `/alerts` | 알림 규칙 생성 |
| PUT | `/alerts/{id}` | 알림 규칙 수정 |
| DELETE | `/alerts/{id}` | 알림 규칙 삭제 |
| POST | `/devices` | FCM 토큰 등록 |
| DELETE | `/devices/{token}` | 토큰 해제 |
| GET | `/notifications?page=` | 받은 알림 이력 |

### 5.8 WebSocket 채널 (`wss://{host}/ws`, STOMP)
| Destination | 방향 | 용도 |
|-------------|------|------|
| `/topic/market/{symbol}` | 구독 | 실시간 시세 틱 |
| `/topic/insight/{symbol}` | 구독 | 전망 카드 갱신 |
| `/topic/chat/{symbol}` | 구독 | 종목 채팅 수신 |
| `/user/queue/orders` | 구독 | Saga 체결·실패 결과 |
| `/app/chat/{symbol}` | 발행 | 채팅 전송 |

---

## 6. FCM/푸시 연동 스펙

### 6.1 Firebase 설정

1. Firebase 콘솔에서 iOS·Android 앱을 각각 등록 (bundle ID: `com.stockpulse.app`, package: `com.stockpulse.app`).
2. `google-services.json` (Android) → `mobile/` 루트에 배치.
3. `GoogleService-Info.plist` (iOS) → `mobile/` 루트에 배치.
4. `app.json` `plugins` 배열에 `expo-notifications` 등록 (아래 7절 참고).

### 6.2 expo-notifications 흐름

```
앱 최초 실행
  └─ setup.ts: requestPermissionsAsync()
        ├─ 거부 → 알림 설정 안내 Alert
        └─ 허용 → getExpoPushTokenAsync() 또는 getDevicePushTokenAsync()
                    └─ POST /devices { token, platform: "ios"|"android" }
```

### 6.3 핸들러 등록 (`notifications/handlers.ts`)

```typescript
// 포그라운드 수신 — 인앱 배너 표시
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

// 백그라운드·종료 상태 — OS 시스템 알림 자동 표시 (expo 기본)

// 알림 탭 응답 — 딥링크 이동
Notifications.addNotificationResponseReceivedListener((response) => {
  const deeplink = response.notification.request.content.data?.deeplink as string;
  if (deeplink) {
    router.push(parseDeeplink(deeplink)); // stockpulse://stock/005930 → /stock/005930
  }
});
```

### 6.4 딥링크 URL 스킴

| URL | 이동 화면 |
|-----|----------|
| `stockpulse://home` | 메인 대시보드 |
| `stockpulse://stock/{symbol}` | 종목 상세 |
| `stockpulse://trading` | 모의투자 |
| `stockpulse://alerts` | 알림 설정 |

`app.json`에 `scheme: "stockpulse"` 설정 필수.  
`expo-linking` `createURL` / `parseURL`로 파싱. `expo-router`의 `Linking.getInitialURL()` 처리로 Cold Start 딥링크도 지원.

### 6.5 FCM 페이로드 수신 타입 (→ `types/notification.ts`)

```typescript
type FcmData = {
  type: 'TARGET_PRICE' | 'CHANGE_RATE' | 'NEWS';
  symbol: string;
  price?: string;
  changeRate?: string;
  newsId?: string;
  headline?: string;
  deeplink: string;
};
```

---

## 7. 빌드/배포 설정

### 7.1 `app.json` 핵심 필드

```json
{
  "expo": {
    "name": "StockPulse",
    "slug": "stockpulse",
    "version": "1.0.0",
    "scheme": "stockpulse",
    "orientation": "portrait",
    "icon": "./assets/icon.png",
    "splash": {
      "image": "./assets/splash.png",
      "resizeMode": "contain",
      "backgroundColor": "#0A0E1A"
    },
    "ios": {
      "supportsTablet": false,
      "bundleIdentifier": "com.stockpulse.app",
      "infoPlist": {
        "NSCameraUsageDescription": "프로필 사진 업로드"
      }
    },
    "android": {
      "package": "com.stockpulse.app",
      "adaptiveIcon": {
        "foregroundImage": "./assets/adaptive-icon.png",
        "backgroundColor": "#0A0E1A"
      },
      "permissions": ["RECEIVE_BOOT_COMPLETED", "VIBRATE"]
    },
    "plugins": [
      [
        "expo-notifications",
        {
          "icon": "./assets/icon.png",
          "color": "#0A0E1A",
          "sounds": []
        }
      ],
      "expo-router",
      "expo-secure-store"
    ],
    "extra": {
      "apiBaseUrl": "https://api.stockpulse.example.com/api/v1",
      "wsUrl": "wss://api.stockpulse.example.com/ws",
      "eas": {
        "projectId": "<EAS_PROJECT_ID>"
      }
    }
  }
}
```

### 7.2 `eas.json` 프로파일

```json
{
  "cli": {
    "version": ">= 10.0.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "env": {
        "APP_ENV": "development"
      }
    },
    "preview": {
      "distribution": "internal",
      "android": { "buildType": "apk" },
      "ios": { "simulator": false },
      "env": {
        "APP_ENV": "staging"
      }
    },
    "production": {
      "distribution": "store",
      "autoIncrement": true,
      "env": {
        "APP_ENV": "production"
      }
    }
  },
  "submit": {
    "production": {
      "android": {
        "serviceAccountKeyPath": "./google-service-account.json",
        "track": "internal"
      },
      "ios": {
        "appleId": "apple@stockpulse.example.com",
        "ascAppId": "<APP_STORE_CONNECT_APP_ID>"
      }
    }
  }
}
```

### 7.3 환경변수 주입

`app.config.ts`로 `app.json`을 동적으로 읽어 `APP_ENV`에 따라 `apiBaseUrl`, `wsUrl`을 환경별로 분기한다. `expo-constants`로 앱 내에서 `Constants.expoConfig.extra.apiBaseUrl` 참조.

---

## 8. 구현 순서 (우선순위 1~5단계)

### 1단계 — 프로젝트 초기화 및 인증 (필수 선행)

**목표**: 앱이 실행되고 로그인·토큰 관리가 동작하는 뼈대 완성.

1. `npx create-expo-app mobile --template expo-template-blank-typescript` 후 구조를 위 트리대로 구성.
2. `tsconfig.json` strict 모드, path alias(`@/` → `src/`) 설정.
3. `app/_layout.tsx`: `QueryClientProvider`, `GestureHandlerRootView`, AuthGuard(`useAuthStore` → 비로그인 시 `/(auth)/login` 리다이렉트) 설정.
4. `src/api/client.ts`: Axios 인스턴스. 요청 인터셉터(헤더 주입), 응답 인터셉터(401 → `POST /auth/refresh` 자동 재시도, 실패 시 로그아웃).
5. `src/store/authStore.ts`: `accessToken`, `refreshToken`, `userId` — `expo-secure-store` 영속 저장.
6. `app/(auth)/login.tsx`: 이메일/비밀번호 폼 + 소셜 로그인 버튼. `POST /auth/login` 성공 시 토큰 저장 + `/(tabs)` 이동.

**완료 기준**: 로그인 → 홈 탭 화면 노출 → 앱 재시작 후 토큰 유효하면 자동 로그인.

---

### 2단계 — 메인 대시보드 + WebSocket 연결

**목표**: 워치리스트 시세 실시간 갱신 화면 완성.

1. `src/websocket/StompClient.ts`: 앱 로그인 후 STOMP 연결(`wss://{wsUrl}`), `Authorization` 헤더 주입, 재연결 backoff 로직 구현.
2. `src/store/wsStore.ts`: `stompClient`, `isConnected`, `subscribe/unsubscribe` 메서드.
3. `app/(tabs)/index.tsx`: `GET /watchlist` + `GET /market/quotes` React Query 조회 → `StockRow` 렌더링.
4. 워치리스트 각 종목에 대해 `/topic/market/{symbol}` 구독 → tick 수신 시 `queryClient.setQueryData`로 가격 즉시 갱신.
5. `GET /insights/strong` 결과로 `InsightCard` 리스트 하단 표시.

**완료 기준**: 워치리스트 화면에서 시세가 실시간으로 갱신되는 것을 확인.

---

### 3단계 — 인사이트 카드 + 종목 상세 + 모의투자

**목표**: "보고 → 행동" 핵심 수직 슬라이스 완성.

1. `src/screens/StockDetailScreen.tsx`: 상단 가격 헤더 + 라인 차트(`react-native-chart-kit`, `GET /market/candles`), 하단 탭(차트·인사이트·뉴스·토론).
2. `InsightCard.tsx`: 4축 점수 바 + 근거 문장 펼치기(`LayoutAnimation`). `GET /insights/{symbol}/factors` 기반.
3. `/topic/insight/{symbol}` 구독 → 전망 카드 실시간 갱신.
4. `app/(tabs)/trading/index.tsx`: 포트폴리오 KPI 카드 + 보유종목 리스트.
5. `OrderPanel.tsx`: 매수/매도 토글, 종목·유형·수량 입력, `POST /orders` mutation.
6. `/user/queue/orders` 구독 → 체결 완료 시 `uiStore` 토스트 + `portfolioQuery.invalidate()`.

**완료 기준**: 종목 상세에서 인사이트 카드 4축 확인 → 모의 매수 → 체결 토스트 수신 → 포트폴리오 반영.

---

### 4단계 — 커뮤니티·랭킹·알림 설정

**목표**: 보조 화면 전부 완성 + FCM 연동.

1. `app/(tabs)/community/[symbol].tsx`: 게시글 FlatList + 실시간 채팅 (`/topic/chat`, `/app/chat`).
2. `app/(tabs)/ranking.tsx`: 기간 탭 + `RankingRow` FlatList + 내 순위 강조.
3. `AlertSettingsScreen.tsx`: 알림 규칙 CRUD + 받은 알림 이력.
4. `notifications/setup.ts`: 앱 시작 시 권한 요청 + `POST /devices` FCM 토큰 등록.
5. `notifications/handlers.ts`: 포그라운드 핸들러 + 알림 탭 딥링크 이동.
6. `app.json`에 `expo-notifications` plugin 설정, `scheme: "stockpulse"` 추가.
7. `utils/deeplink.ts`: `stockpulse://stock/{symbol}` 파싱 → expo-router 경로 변환.

**완료 기준**: FCM 테스트 알림 전송 → 포그라운드 배너 + 탭 후 종목 상세 화면 이동 확인.

---

### 5단계 — EAS Build 설정·최종 검증

**목표**: 배포 가능한 빌드 산출 + DoD 전체 검증.

1. `eas.json` 작성 (`development`, `preview`, `production` 프로파일).
2. `eas build --platform android --profile preview` → APK 생성 + 실기기 설치 테스트.
3. `eas build --platform ios --profile preview` → TestFlight 업로드 또는 시뮬레이터 빌드.
4. 환경변수 (`apiBaseUrl`, `wsUrl`) EAS Secret 등록 또는 `app.config.ts` 분기 처리.
5. DoD 1~7 항목 순서대로 체크리스트 검증 (실기기 + 실 백엔드 연동).
6. 면책 문구("본 서비스는 투자자문이 아닌 공개 데이터 정보 제공입니다. 모든 거래는 가상입니다.") 모의투자 화면 상단에 표시.

**완료 기준**: `eas build` 성공 + QR코드로 앱 설치 + DoD 전 항목 통과.
