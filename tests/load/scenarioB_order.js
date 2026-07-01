/**
 * 시나리오 B — 모의투자 주문 부하 테스트
 * 대상: POST /api/v1/orders  (trading-service via api-gateway)
 * 특성: 202 Accepted + Saga 비동기. 동기 응답시간만 측정
 * 목표: p95 < 500ms, error_rate < 1% (비즈니스 거절 409 제외)
 *
 * 주의: 테스트 전 잔고 충전 필요
 *   curl -X POST http://localhost:8080/api/v1/account/reset \
 *        -H "Authorization: Bearer $K6_JWT_TOKEN"
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders, checkJwt, commonThresholds } from './lib/config.js';
import { randomSymbol } from './lib/symbols.js';

const businessRejected = new Counter('business_rejected');

export const options = {
  scenarios: {
    order_load: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 50 },
        { duration: '2m',  target: 50 },
        { duration: '1m',  target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...commonThresholds,
    'http_req_duration{scenario:order_load}': ['p(95)<500'],
  },
};

export function setup() {
  checkJwt();
}

export default function () {
  const payload = JSON.stringify({
    symbol:   randomSymbol(),
    side:     'BUY',
    type:     'MARKET',
    quantity: 1,
  });

  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    payload,
    { ...authHeaders(), tags: { scenario: 'order_load' } }
  );

  // 잔고 부족(409)은 비즈니스 거절로 분리 집계 (error_rate에서 제외)
  if (res.status === 409) {
    businessRejected.add(1);
  }

  check(res, {
    'status 202':          (r) => r.status === 202,
    'data.orderId exists': (r) => r.json('data.orderId') !== undefined,
  });

  sleep(1);
}
