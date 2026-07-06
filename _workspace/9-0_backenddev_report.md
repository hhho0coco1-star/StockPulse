# Phase 9 구현 보고서

## 생성 파일 목록 (경로 + 핵심 로직 1줄 설명)

### 루트 설정 파일 (`mobile/` 기준)

| 파일 | 핵심 로직 |
|------|----------|
| `package.json` | Expo SDK 52, RN 0.76, @tanstack/react-query ^5, zustand ^4, @stomp/stompjs ^7 포함 전체 의존성 |
| `tsconfig.json` | strict: true, `@/*` → `src/*` path alias 설정 |
| `app.json` | scheme: "stockpulse", expo-notifications/expo-router/expo-secure-store 플러그���, EAS extra 필드 |
| `eas.json` | development(devClient)/preview(APK·internal)/production(store·autoIncrement) 3개 프로파일 |
| `babel.config.js` | babel-preset-expo + react-native-reanimated/plugin + module-resolver(@/ alias) |
| `.env.example` | API_BASE_URL, WS_BASE_URL, APP_ENV 환경변수 예시 |

### Expo Router 라우트 (`mobile/app/`)

| 파일 | 핵심 로직 |
|------|----------|
| `app/_layout.tsx` | QueryClientProvider + GestureHandlerRootView + AuthGuard(인증 리다이렉트) + FCM 핸들러 ���기화 |
| `app/(auth)/_layout.tsx` | 인증 ��룹 Stack 레이아웃 |
| `app/(auth)/login.tsx` | 이메일/비밀번호 useMutation → setTokens → connectStomp → setupPushNotifications 순서 실행 |
| `app/(auth)/signup.tsx` | 회원가입 후 로그인과 동일한 초기화 흐름 |
| `app/(tabs)/_layout.tsx` | Tabs 5개(홈·인사이트·모의투자·커뮤니티·랭킹), Ionicons 아이콘, 어두운 탭바 |
| `app/(tabs)/index.tsx` | 워치리스트 React Query + `/topic/market/{symbol}` STOMP 구독 → setQueryData 실시간 갱신 |
| `app/(tabs)/insight.tsx` | useInfiniteQuery��� 강세 인사이트 페이지네이션 |
| `app/(tabs)/trading/index.tsx` | KPI 4개 + 포트폴리오 리스트 + `/user/queue/orders` Saga 결과 구독 + 토스트 표시 |
| `app/(tabs)/trading/order.tsx` | 개별 종목 주문 상세 화면, OrderPanel 래핑 |
| `app/(tabs)/community/index.tsx` | 종목 검색 후 토론방 진입 |
| `app/(tabs)/community/[symbol].tsx` | 게시글 FlatList + 실시간 ��팅(`/topic/chat/{symbol}` 구독, `/app/chat/{symbol}` 발행) 탭 분리 |
| `app/(tabs)/ranking.tsx` | 기간 탭(일간·주간·전체) + useInfiniteQuery 페이지네이션 + 내 순위 강조 배지 |
| `app/stock/[symbol].tsx` | StockDetailScreen 래핑 (딥링크 `stockpulse://stock/{symbol}` 진입점) |
| `app/alerts.tsx` | AlertSettingsScreen 래핑 |

### API 계층 (`mobile/src/api/`)

| 파일 | 핵심 로직 |
|------|----------|
| `client.ts` | Axios 인스턴스, 요청 인터셉터(Bearer 주입), 401 응답 인터셉터(큐 방식 토큰 재발급·로그아웃) |
| `auth.ts` | /auth/login·signup·logout, /users/me |
| `market.ts` | /market/symbols·quote·quotes·candles + /watchlist CRUD |
| `insight.ts` | /insights/{symbol}·/factors·/strong (페이지) |
| `trading.ts` | /orders·/account·/portfolio·/portfolio/summary + /ranking·/ranking/me |
| `community.ts` | /community/{symbol}/posts·/comments·/like |
| `notification.ts` | /alerts CRUD, /devices, /notifications |

### 상태 관리 (`mobile/src/store/`)

| 파일 | 핵심 로직 |
|------|----------|
| `authStore.ts` | Zustand: accessToken·refreshToken·user + expo-secure-store 영속 저장 |
| `wsStore.ts` | Zustand: STOMP Client 인스턴스 + 구독 Map 관리 |
| `uiStore.ts` | Zustand: 토스트 큐(3초 자동 제거) + 글로벌 로딩 플래그 |

### WebSocket (`mobile/src/websocket/`)

| 파일 | 핵심 로직 |
|------|----------|
| `StompClient.ts` | STOMP 싱글톤 클라이언트, SockJS transport, beforeConnect에서 토큰 주입, 5초 재연결 backoff |
| `subscriptions.ts` | /topic/market·insight·chat + /user/queue/orders 구독 헬퍼, /app/chat 발행 함수 |

### 알림 (`mobile/src/notifications/`)

| 파일 | 핵심 로직 |
|------|----------|
| `setup.ts` | 권한 요청 → getDevicePushTokenAsync → POST /devices, Android 알림 채널 생성 |
| `handlers.ts` | setNotificationHandler(포그라운드 배너), addNotificationResponseReceivedListener(딥링크 이동) |

### 컴포넌트 (`mobile/src/components/`)

| 파일 | 핵심 로직 |
|------|----------|
| `PriceTag.tsx` | 상승=빨강(#FF3B30)·하락=파랑(#007AFF) 색 규칙 배지 |
| `StockRow.tsx` | 종목명·코드·가격·PriceTag 1행 |
| `InsightCard.tsx` | 4축 점수 바 + LayoutAnimation 펼치기/접기, 면책 문구 |
| `OrderPanel.tsx` | 매수/매도 토글, 시장가/지정가 토글, 수량·��격 입력, POST /orders 콜백 |
| `PostItem.tsx` | 좋아요·댓글 수 표시, Ionicons heart 아이콘 |
| `ChatMessage.tsx` | 내 메시지(오른쪽·파랑) vs 상대방(왼쪽·회색) ��블 |
| `RankingRow.tsx` | 1~3위 금색, 내 순위 배경 강조 |
| `AlertRuleItem.tsx` | Switch 토글 + 삭제 버튼, 규칙 조건 텍스트 표시 |

### 스크린 (`mobile/src/screens/`)

| 파일 | 핵심 로직 |
|------|----------|
| `StockDetailScreen.tsx` | 가격 헤더 + LineChart(react-native-chart-kit) + 차트/인사이트/토론 탭 + 하단 모의매수/매도 버튼 |
| `AlertSettingsScreen.tsx` | 알림 ��칙 CRUD (Modal 폼) + 알림 이력 FlatList |

### 타입 정의 (`mobile/src/types/`)

| 파일 | 내용 |
|------|------|
| `api.ts` | ApiResponse\<T\>, ApiError, PageResponse\<T\> |
| `market.ts` | StockSymbol, StockQuote, Candle, MarketTick, WatchlistItem |
| `insight.ts` | InsightSummary, InsightFactors, InsightFactor, InsightUpdate |
| `trading.ts` | Order, OrderRequest, Account, PortfolioItem, PortfolioSummary, RankingEntry, MyRanking |
| `notification.ts` | AlertRule, AlertRuleRequest, DeviceToken, NotificationHistory, FcmData |
| `community.ts` | Post, Comment, ChatMessage, PostRequest, CommentRequest, ChatSendRequest |

### 유틸리티 (`mobile/src/utils/`)

| 파일 | 핵심 로직 |
|------|----------|
| `storage.ts` | expo-secure-store 래퍼 (ACCESS_TOKEN·REFRESH_TOKEN·USER_ID) |
| `format.ts` | formatPrice, formatChangeRate, formatAmount, formatProfitRate, formatDate, formatVolume |
| `deeplink.ts` | stockpulse:// → expo-router 경로 변환 (parseDeeplink/toDeeplink) |

---

## 미구현/보류 항목 (이유 포함)

| 항목 | 이유 |
|------|------|
| **assets 이미지 파일** (`icon.png`, `splash.png`, `adaptive-icon.png`) | 바이너�� 에셋은 코드로 생성 불가. EAS Build 전 디자인 팀 제공 또는 Expo 기본값 사용 필요 |
| **`app.config.ts` 환경 분기** | 스펙 7.3에 명시되어 있으나 `app.json`의 `extra` 필드로 기본값 제공. 실제 배포 전 APP_ENV별 분기 코드 추가 권장 |
| **`google-services.json` / `GoogleService-Info.plist`** | Firebase 콘솔에서 실제 앱 등록 후 발급되는 파일. 현재 플레이스홀더 없음 (gitignore 대상) |
| **소셜 로그인 (Google/Kakao)** | `GET /auth/oauth2/{provider}` API는 정의했으나 웹뷰/OAuth 흐름 UI는 스펙에 상세 미정의. 로그인 화면에 버튼 자리 확보 가능 |
| **종목 상세 뉴스 탭** | 스펙 4.2에 "뉴스" 탭이 있으나 뉴스 전용 API 엔드포인트가 03_API설계.md에 없음. 백엔드 추가 후 구현 가능 |
| **포트폴리오 수익률 ��트** (`react-native-chart-kit`) | 스펙에 간단 라인차트 언급 있음. 종목 상세에는 캔들 기반 LineChart 구현. 포트폴리오 화면의 수익률 시계열 차트는 `/portfolio/history` 등 별도 API 필요 |
| **`watchlist.ts` 독립 파일** | 스펙 파일 목록에 명시되어 있으나 `src/api/market.ts` 내 `watchlistApi`로 통합. 별도 분리 시 import 경로 변경만 필요 |
| **`src/hooks/` 독립 훅 파일** | 스펙에 `useWatchlist.ts` 등 훅 파일 목록 있으나, 각 화면에서 React Query를 직접 사용하는 패턴 채택. 중복 쿼리가 많아질 경우 훅으로 추출 권장 |
| **EAS Build 실행 검증** | 로컬에 `eas-cli`·Firebase 설정 없음. `eas.json` 및 `app.json` 설정은 완료. 실기기 검증은 Firebase 앱 등록 후 가능 |

---

## QA 검증 요청 사항

### DoD 체크리스트 기준

1. **시세판 실시간 갱신**
   - 로그인 후 `app/(tabs)/index.tsx` 진입 → `connectStomp()` 호출 확인
   - wsStore.isConnected === true 시 subscribeMarketTick 등록 확인
   - 백엔드 WebSocket 서버에서 `/topic/market/005930` tick 발행 → UI 가격 즉시 갱신 확인

2. **모의 주문 Saga 완료 통지**
   - `app/(tabs)/trading/index.tsx`에서 OrderPanel submit → POST /orders → 202 orderId 수신 확인
   - subscribeOrderQueue 구독 → `COMPLETED` 수신 시 showToast('체결 완료') + portfolioQuery.invalidate 확인

3. **FCM 포그라��드/백그라운드 수신**
   - `notifications/setup.ts`: requestPermissionsAsync 거부 시 Alert 표시 확인
   - `notifications/handlers.ts`: setNotificationHandler shouldShowAlert: true 동작 확인
   - 실기기에서 FCM 테스트 메시지 발송 → 포그라운드 배너 수신 확인

4. **딥링크 이동**
   - `utils/deeplink.ts`: `stockpulse://stock/005930` �� `/stock/005930` 변환 단위 테스트
   - 알림 탭 응답 시 router.push(parseDeeplink(deeplink)) 호출 확인
   - Cold Start: `app.json` scheme: "stockpulse" 설정 → OS 레벨 딥링크 열기 테스트

5. **EAS Build 성공**
   - `eas.json` preview 프로파일 확인
   - Firebase 파일 배치 후 `eas build --platform android --profile preview` 실행

6. **인사이트 카드 렌더링**
   - `GET /insights/{symbol}/factors` 4축(momentum·earnings·valuation·news) 응답 확인
   - `InsightCard.tsx` factorRow 4개 렌더링 + 점수 바 너비(score%) 확인

7. **커뮤니티 실시간 채팅**
   - `app/(tabs)/community/[symbol].tsx` 채팅 탭 → subscribeChat 등록 확인
   - 채팅 입력 → sendChatMessage(`/app/chat/${symbol}`) 발행 → `/topic/chat/${symbol}` 수신 → chatMessages append 확인

### 추가 검증 항목
- TypeScript 컴파일 오류 없음: `npx tsc --noEmit` 실행
- `@/` path alias 정상 해석 확인 (`babel.config.js` module-resolver)
- 앱 재시작 후 자동 로그인 (`authStore.loadFromStorage` → setTokens → AuthGuard 리다이렉트)
- 401 토큰 만료 → 자동 재발급 → 원래 요청 재시도 (`api/client.ts` 인터셉터)
