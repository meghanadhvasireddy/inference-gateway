import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const levels = [1, 10, 50, 100, 250];
const seconds = Number(__ENV.STAGE_SECONDS || 20);
const cacheMode = __ENV.CACHE_MODE || 'unique';
const baseUrl = __ENV.BASE_URL || 'http://inference-gateway:8080';
const cacheHits = new Counter('cache_hits');
const requestErrors = new Rate('request_errors');

const scenarios = {};
const thresholds = {};
levels.forEach((vus, index) => {
  const name = `c${vus}`;
  scenarios[name] = {
    executor: 'constant-vus', vus, duration: `${seconds}s`, startTime: `${index * seconds}s`,
    exec: 'infer', gracefulStop: '1s',
  };
  thresholds[`http_req_duration{scenario:${name}}`] = ['p(95)<30000'];
  thresholds[`http_reqs{scenario:${name}}`] = ['count>0'];
  thresholds[`http_req_failed{scenario:${name}}`] = ['rate<1'];
  thresholds[`cache_hits{scenario:${name}}`] = ['count>=0'];
});

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)'],
};

export function infer() {
  const prompt = cacheMode === 'repeat'
    ? `Explain binary search, variant ${__VU % 10}`
    : `Explain binary search, request ${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(`${baseUrl}/api/v1/inference`, JSON.stringify({
    model: 'mock-llm', prompt, temperature: 0.7, maxTokens: 100,
  }), { headers: { 'Content-Type': 'application/json' }, timeout: '30s' });
  const good = check(response, { 'HTTP 200': r => r.status === 200 });
  requestErrors.add(!good);
  if (good) {
    try { cacheHits.add(response.json('cached') === true ? 1 : 0); }
    catch (_) { requestErrors.add(true); }
  }
}

export function handleSummary(data) {
  const stages = levels.map(vus => {
    const name = `c${vus}`;
    const latency = data.metrics[`http_req_duration{scenario:${name}}`]?.values || {};
    const requests = data.metrics[`http_reqs{scenario:${name}}`]?.values || {};
    const failures = data.metrics[`http_req_failed{scenario:${name}}`]?.values || {};
    const hits = data.metrics[`cache_hits{scenario:${name}}`]?.values || {};
    return {
      vus, requests: requests.count || 0,
      throughputRps: Number(((requests.count || 0) / seconds).toFixed(2)),
      avgMs: latency.avg ?? null,
      p50Ms: latency['p(50)'] ?? null,
      p95Ms: latency['p(95)'] ?? null,
      p99Ms: latency['p(99)'] ?? null,
      errorRate: failures.rate ?? null,
      cacheHitRate: requests.count ? (hits.count || 0) / requests.count : null,
    };
  });
  const result = {
    measuredAt: new Date().toISOString(), cacheMode, stageSeconds: seconds, baseUrl,
    overallErrorRate: data.metrics.http_req_failed?.values?.rate ?? null,
    cacheHits: data.metrics.cache_hits?.values?.count ?? null,
    stages,
  };
  return { stdout: JSON.stringify(result, null, 2) + '\n', '/scripts/results.json': JSON.stringify(result, null, 2) + '\n' };
}
