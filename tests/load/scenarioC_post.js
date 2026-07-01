/**
 * 시나리오 C — 토론방 게시글 작성 부하 테스트
 * 대상: POST /api/v1/community/{symbol}/posts  (discussion-service via api-gateway)
 * 특성: PostgreSQL write + Outbox
 * 목표: p95 < 500ms, error_rate < 1%
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, authHeaders, checkJwt, commonThresholds } from './lib/config.js';
import { randomSymbol } from './lib/symbols.js';

export const options = {
  scenarios: {
    post_load: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 30 },
        { duration: '2m',  target: 30 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    ...commonThresholds,
    'http_req_duration{scenario:post_load}': ['p(95)<500'],
  },
};

export function setup() {
  checkJwt();
}

export default function () {
  const symbol = randomSymbol();
  const payload = JSON.stringify({
    content: `load test VU=${__VU} iter=${__ITER} ts=${Date.now()}`,
  });

  const res = http.post(
    `${BASE_URL}/api/v1/community/${symbol}/posts`,
    payload,
    { ...authHeaders(), tags: { scenario: 'post_load' } }
  );

  check(res, {
    'status 200 or 201':   (r) => r.status === 200 || r.status === 201,
    'data.postId exists':  (r) => r.json('data.postId') !== undefined,
  });

  sleep(1);
}
