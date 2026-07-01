export const BASE_URL = __ENV.K6_BASE_URL || 'http://localhost:8080';

const JWT = __ENV.K6_JWT_TOKEN;

export function checkJwt() {
  if (!JWT) {
    throw new Error('K6_JWT_TOKEN is required. Set it with: export K6_JWT_TOKEN="<access_token>"');
  }
}

export function authHeaders() {
  return {
    headers: {
      'Authorization': `Bearer ${JWT}`,
      'Content-Type': 'application/json',
    },
  };
}

export const commonThresholds = {
  http_req_failed: ['rate<0.01'],
  http_req_duration: ['p(95)<500'],
};
