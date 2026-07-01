/**
 * 혼합 부하 테스트 — 시나리오 A+B+C 동시 실행
 * A(시세 조회) 즉시 시작, B(주문) +10s, C(게시글) +20s
 * 실서비스와 유사한 트래픽 믹스 재현
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, authHeaders, checkJwt } from './lib/config.js';
import { randomSymbol } from './lib/symbols.js';

const businessRejected = new Counter('business_rejected');

export const options = {
  scenarios: {
    quote: {
      executor: 'ramping-arrival-rate',
      startTime: '0s',
      startRate: 0,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { duration: '30s', target: 200 },
        { duration: '3m',  target: 200 },
        { duration: '30s', target: 0 },
      ],
      exec: 'quoteScenario',
    },
    order: {
      executor: 'ramping-vus',
      startTime: '10s',
      stages: [
        { duration: '30s', target: 30 },
        { duration: '2m',  target: 30 },
        { duration: '30s', target: 0 },
      ],
      exec: 'orderScenario',
    },
    post: {
      executor: 'ramping-vus',
      startTime: '20s',
      stages: [
        { duration: '30s', target: 15 },
        { duration: '2m',  target: 15 },
        { duration: '30s', target: 0 },
      ],
      exec: 'postScenario',
    },
  },
  thresholds: {
    'http_req_failed':                    ['rate<0.01'],
    'http_req_duration{scenario:quote}':  ['p(95)<300'],
    'http_req_duration{scenario:order}':  ['p(95)<500'],
    'http_req_duration{scenario:post}':   ['p(95)<500'],
  },
};

export function setup() {
  checkJwt();
}

export function quoteScenario() {
  const symbol = randomSymbol();
  const res = http.get(
    `${BASE_URL}/api/v1/market/quote/${symbol}`,
    { ...authHeaders(), tags: { scenario: 'quote' } }
  );
  check(res, { 'quote 200': (r) => r.status === 200 });
}

export function orderScenario() {
  const payload = JSON.stringify({ symbol: randomSymbol(), side: 'BUY', type: 'MARKET', quantity: 1 });
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    payload,
    { ...authHeaders(), tags: { scenario: 'order' } }
  );
  if (res.status === 409) businessRejected.add(1);
  check(res, { 'order 202': (r) => r.status === 202 });
  sleep(1);
}

export function postScenario() {
  const symbol = randomSymbol();
  const payload = JSON.stringify({ content: `mixed load VU=${__VU} ${Date.now()}` });
  const res = http.post(
    `${BASE_URL}/api/v1/community/${symbol}/posts`,
    payload,
    { ...authHeaders(), tags: { scenario: 'post' } }
  );
  check(res, { 'post 200/201': (r) => r.status === 200 || r.status === 201 });
  sleep(1);
}
