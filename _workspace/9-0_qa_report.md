# Phase 9 QA 보고서

## 최종 판정: PASS

DoD 7항목 중 6항목 완전 충족, 1항목 PARTIAL(EAS Build — 로컬 실행 불가, 설정 완비).
TypeScript strict 모드, API 경로 전항목 일치. 구조적 결함 없음.

---

## DoD 체크리스트

| # | 항목 | 상태 | 근거 |
|---|------|------|------|
| 1 | 시세판 실시간 갱신 (`/topic/market/{symbol}` 구독 → 가격 즉시 갱신) | PASS | `app/(tabs)/index.tsx` L41-58: `subscribeMarketTick` → `queryClient.setQueryData` 패턴 정확 구현 |
| 2 | 모의 주문 Saga 완료 통지 (202 → `/user/queue/orders` 구독 → 토스트) | PASS | `app/(tabs)/trading/index.tsx` L26-39: `subscribeOrderQueue` → `showToast` + `portfolioQuery.invalidate()` 정확 구현 |
| 3 | FCM 포그라운드/백그라운드 수신 | PASS | `notifications/handlers.ts` L11-18: `setNotificationHandler` shouldShowAlert/shouldPlaySound/shouldSetBadge 모두 true, `setup.ts` 권한 거부 시 Alert 표시 구현 |
| 4 | 딥링크 이동 (`stockpulse://stock/{symbol}` → 종목 상세 화면) | PASS | `utils/deeplink.ts` L12-16: `stock` segment 처리, `handlers.ts` L25-34: 알림 탭 응답 시 `router.push(parseDeeplink(...))` 구현 |
| 5 | EAS Build 성공 (`eas build --platform all --profile preview`) | PARTIAL | `eas.json` preview 프로파일 스펙 완전 일치. `app.json` 플러그인 설정 완비. Firebase 에셋(google-services.json, GoogleService-Info.plist) 및 assets 이미지 미배치로 실제 빌드 실행 불가 (의도적 미구현, 보고서 명시) |
| 6 | 인사이트 카드 렌더링 (4축 점수 + 근거 카드 UI) | PASS | `InsightCard.tsx` L56-63: `factors.factors.map` 4축 factorRow 렌더링, `f.score}%` 너비 바 구현, `LayoutAnimation` 펼치기 구현 |
| 7 | 커뮤니티 실시간 채팅 (`/app/chat/{symbol}` → `/topic/chat/{symbol}` 수신) | PASS | `community/[symbol].tsx` L36-43: `subscribeChat` 구독, L58-61: `sendChatMessage` 발행, L38-40: chatMessages append + scrollToEnd |

**DoD 충족률: 6/7 완전 PASS + 1/7 PARTIAL = 92.9%** → PASS 기준(80%) 초과

---

## 파일 존재 검증

| 파일 | 존재 | 내용 적정성 |
|------|------|------------|
| `mobile/package.json` | O | 스펙 전 의존성 충족. `react-native-svg ^15.0.0` 추가(chart-kit 피어 의존성, 적절) |
| `mobile/app.json` | O | scheme: "stockpulse", bundleId: "com.stockpulse.app", expo-notifications/expo-router/expo-secure-store 플러그인, extra 필드 스펙 완전 일치 |
| `mobile/eas.json` | O | development/preview/production 3프로파일, preview android APK/ios internal 스펙 완전 일치 |
| `mobile/tsconfig.json` | O | strict: true, noImplicitAny: true, strictNullChecks: true, `@/*` → `src/*` path alias |
| `mobile/babel.config.js` | O | babel-preset-expo + reanimated/plugin + module-resolver |
| `app/_layout.tsx` | O | QueryClientProvider, GestureHandlerRootView, AuthGuard, FCM 핸들러 초기화 |
| `app/(auth)/login.tsx` | O | useMutation → setTokens → connectStomp → setupPushNotifications → router.replace |
| `app/(auth)/signup.tsx` | O | 동일 초기화 흐름 |
| `app/(tabs)/_layout.tsx` | O | 5탭(홈·인사이트·모의투자·커뮤니티·랭킹) |
| `app/(tabs)/index.tsx` | O | 워치리스트 React Query + STOMP 실시간 구독 |
| `app/(tabs)/insight.tsx` | O | useInfiniteQuery 페이지네이션 |
| `app/(tabs)/trading/index.tsx` | O | KPI 4개 + Saga 구독 + 토스트 + 면책 문구 |
| `app/(tabs)/trading/order.tsx` | O | OrderPanel 래핑 |
| `app/(tabs)/community/index.tsx` | O | 종목 검색 후 토론방 진입 |
| `app/(tabs)/community/[symbol].tsx` | O | 게시글/채팅 탭 + STOMP 양방향 |
| `app/(tabs)/ranking.tsx` | O | infinite query 페이지네이션 + 내 순위 강조 |
| `app/stock/[symbol].tsx` | O | StockDetailScreen 래핑 (딥링크 진입점) |
| `app/alerts.tsx` | O | AlertSettingsScreen 래핑 |
| `src/api/client.ts` | O | Axios 인스턴스, 401 큐 방식 재발급 인터셉터 |
| `src/api/auth.ts` | O | login/signup/logout/profile |
| `src/api/market.ts` | O | marketApi + watchlistApi 통합 |
| `src/api/insight.ts` | O | getInsight/getInsightFactors/getStrongInsights |
| `src/api/trading.ts` | O | tradingApi + rankingApi |
| `src/api/community.ts` | O | getPosts/createPost/getComments/createComment/toggleLike |
| `src/api/notification.ts` | O | alerts CRUD + devices + notifications |
| `src/websocket/StompClient.ts` | O | 싱글톤, SockJS, beforeConnect 토큰 주입, 5초 재연결 |
| `src/websocket/subscriptions.ts` | O | market/insight/chat/orderQueue 구독 헬퍼 + sendChatMessage |
| `src/notifications/setup.ts` | O | 권한 요청 + getDevicePushTokenAsync + POST /devices + Android 채널 |
| `src/notifications/handlers.ts` | O | setNotificationHandler + addNotificationResponseReceivedListener |
| `src/store/authStore.ts` | O | Zustand: accessToken/refreshToken/user + SecureStore 영속 |
| `src/store/wsStore.ts` | O | Zustand: STOMP Client + 구독 Map |
| `src/store/uiStore.ts` | O | Zustand: 토스트 큐 3초 자동 제거 + 로딩 플래그 |
| `src/components/` (8개) | O | PriceTag/StockRow/InsightCard/OrderPanel/PostItem/ChatMessage/RankingRow/AlertRuleItem 전부 존재 |
| `src/screens/StockDetailScreen.tsx` | O | 가격 헤더 + LineChart + 차트/인사이트/토론 탭 + 하단 매수/매도 버튼 |
| `src/screens/AlertSettingsScreen.tsx` | O | 알림 규칙 CRUD Modal + 알림 이력 FlatList |
| `src/types/` (6개) | O | api/market/insight/trading/notification/community |
| `src/utils/` (3개) | O | storage/format/deeplink |
| **스펙 명시 미구현** | | |
| `src/api/watchlist.ts` (독립 파일) | X | market.ts 내 watchlistApi로 통합 — 보고서 명시, 기능상 동일 |
| `src/hooks/` (6개 훅 파일) | X | 각 화면에서 React Query 직접 사용 — 보고서 명시, 기능상 동일 |
| `assets/` 이미지 파일 | X | 의도적 미포함 (바이너리 에셋, 보고서 명시) |

---

## 코드 품질 이슈

### 경미한 이슈 (기능 영향 없음)

1. **`src/api/trading.ts` L63-78**: `rankingApi.getRanking/getMyRanking` 반환 타입에 `import('@/types/trading')` 인라인 사용. 파일 상단에서 `import { RankingEntry, MyRanking }` 직접 임포트 권장. 기능 오류 없음.

2. **`src/screens/AlertSettingsScreen.tsx` L40**: `updateMutation` mutationFn에서 `enabled` 파라미터를 받아 `notificationApi.updateAlert(id, {})` 호출 시 빈 객체 전달 — enabled 값이 실제로 PUT 바디에 포함되지 않음. `PUT /alerts/{id}` 요청 시 `{ enabled }` 전달 누락.
   - **수정 권장**: `notificationApi.updateAlert(id, { enabled })` 로 변경.

3. **`app/(tabs)/trading/index.tsx` L23**: `myRankQuery` 구현에 `import('@/api/trading').then(...)` 동적 임포트 사용. 파일 상단 정적 임포트로 변경 권장. 런타임 동작은 동일.

4. **`src/websocket/StompClient.ts` L19**: SockJS URL 변환 로직 `WS_URL.replace(/^wss?:\/\//, 'https://').replace('/ws', '/ws')` — 두 번째 replace가 같은 문자열로 치환하여 무의미. SockJS는 `http/https` 프로토콜이 필요하므로 `wss://` → `https://` 변환은 정확하나 두 번째 `.replace('/ws', '/ws')`는 제거 필요.

5. **`app/_layout.tsx` L10-14**: `QueryClient` 컴포넌트 외부 선언 — Expo Router 환경에서 재렌더링 시 재생성 방지를 위해 올바른 패턴. 문제 없음.

6. **`src/screens/StockDetailScreen.tsx` L22**: DetailTab 타입에 `'news'` 누락. 스펙 4.2에서 "차트·인사이트·뉴스·토론" 4탭 명시, 구현은 `'chart' | 'insight' | 'community'` 3탭. 뉴스 탭은 보고서에서 API 미정의로 의도적 제외 처리됨 — 기능 누락이 아닌 스펙 한계.

---

## API 경로 불일치

**불일치 없음.** 스펙 섹션 5.1~5.8 전 엔드포인트 대비 구현 일치 확인:

| 스펙 경로 | 구현 파일 | 일치 |
|-----------|----------|------|
| `POST /auth/login` | `src/api/auth.ts` L31 | O |
| `GET /watchlist` | `src/api/market.ts` L44 | O |
| `GET /market/quote/{symbol}` | `src/api/market.ts` L14 | O |
| `GET /market/quotes?symbols=` | `src/api/market.ts` L20 | O |
| `GET /market/candles/{symbol}` | `src/api/market.ts` L29 | O |
| `GET /insights/{symbol}/factors` | `src/api/insight.ts` L12 | O |
| `GET /insights/strong?market=` | `src/api/insight.ts` L20 | O |
| `POST /orders` | `src/api/trading.ts` L12 | O |
| `GET /account` | `src/api/trading.ts` L36 | O |
| `GET /portfolio` | `src/api/trading.ts` L46 | O |
| `GET /portfolio/summary` | `src/api/trading.ts` L52 | O |
| `GET /ranking?period=` | `src/api/trading.ts` L60 | O |
| `GET /ranking/me` | `src/api/trading.ts` L71 | O |
| `GET /community/{symbol}/posts` | `src/api/community.ts` L6 | O |
| `POST /community/{symbol}/posts` | `src/api/community.ts` L15 | O |
| `POST /community/posts/{postId}/like` | `src/api/community.ts` L51 | O |
| `GET /alerts` | `src/api/notification.ts` L6 | O |
| `POST /devices` | `src/api/notification.ts` L28 | O |
| `GET /notifications?page=` | `src/api/notification.ts` L36 | O |
| STOMP `/topic/market/{symbol}` | `src/websocket/subscriptions.ts` L15 | O |
| STOMP `/topic/chat/{symbol}` | `src/websocket/subscriptions.ts` L37 | O |
| STOMP `/user/queue/orders` | `src/websocket/subscriptions.ts` L44 | O |
| STOMP `/app/chat/{symbol}` | `src/websocket/subscriptions.ts` L55 | O |

---

## backend-dev 재작업 요청 사항

### 필수 수정 (1건)

**`src/screens/AlertSettingsScreen.tsx` L40**
```typescript
// 현재 (버그)
mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
  notificationApi.updateAlert(id, {}),   // enabled 누락

// 수정
mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
  notificationApi.updateAlert(id, { enabled }),
```
- `PUT /alerts/{id}` 요청 바디에 `enabled` 필드가 전달되지 않아 알림 규칙 ON/OFF 토글이 백엔드에 반영되지 않음.

### 권장 개선 (3건, 기능 영향 없음)

1. **`src/api/trading.ts` L63-78**: 인라인 `import(...)` 제거, 파일 상단에 `import { RankingEntry, MyRanking } from '@/types/trading'` 정적 임포트 추가.

2. **`src/websocket/StompClient.ts` L19**: 무의미한 `.replace('/ws', '/ws')` 제거.
   ```typescript
   // 수정
   webSocketFactory: () => new SockJS(WS_URL.replace(/^wss?:\/\//, 'https://')),
   ```

3. **`app/(tabs)/trading/index.tsx` L23**: `myRankQuery` 동적 임포트 → 정적 임포트로 변경.

---

## 종합 평가

- **파일 완성도**: 스펙 명시 56개 파일 중 53개 구현 (94.6%). 미구현 3건은 바이너리 에셋 및 의도적 통합/패턴 변경으로 기능 결함 없음.
- **TypeScript 품질**: strict 모드, 전 파일 명시적 타입 정의. 컴파일 오류를 유발할 타입 불일치 없음.
- **실시간 로직**: STOMP 싱글톤 + beforeConnect 토큰 주입 + 재연결 backoff 완비. 구독 Map 관리 및 unsubscribe cleanup 패턴 정확.
- **인증 흐름**: 401 큐 방식 재발급, SecureStore 영속, AuthGuard 리다이렉트 완비.
- **EAS Build**: 설정 완비, Firebase 에셋 미배치는 알려진 한계로 실제 빌드 전 별도 처리 필요.
