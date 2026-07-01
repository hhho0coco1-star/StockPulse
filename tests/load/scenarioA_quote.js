/**
 * 시나리오 A — 시세 조회 부하 테스트
 * 대상: GET /api/v1/market/quote/{symbol}  (market-collector via api-gateway)
 * 특성: Redis 캐시 hit 경로, read-heavy
 * 목표: p95 < 300ms, error_rate < 1%, 최대 1000 RPS
 */
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, authHeaders, checkJwt, commonThresholds } from './lib/config.js';
import { randomSymbol } from './lib/symbols.js';

export const options = {
  executor: 'ramping-arrival-rate',
  scenarios: {
    quote_load: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      stages: [
        { duration: '30s', target: 500 },
        { duration: '2m',  target: 500 },
        { duration: '1m',  target: 1000 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...commonThresholds,
    'http_req_duration{scenario:quote_load}': ['p(95)<300'],
  },
};

export function setup() {
  checkJwt();
}

export default function () {
  const symbol = randomSymbol();
  const res = http.get(
    `${BASE_URL}/api/v1/market/quote/${symbol}`,
    { ...authHeaders(), tags: { scenario: 'quote_load' } }
  );

  check(res, {
    'status 200':         (r) => r.status === 200,
    'success true':       (r) => r.json('success') === true,
    'data.price exists':  (r) => r.json('data.price') !== undefined,
  });
}
