// Rate limiter test: GET /junes/api/v1/product/products/{platform}
// Goal: prove the Bucket4j/Redis rate limiter (100 req/hour per IP,
// see RateLimitConfig + ProductController's @RateLimit) correctly throttles
// a single abusive client instead of the app falling over.
//
// Unlike product-listing-throughput.js, every request here comes from the
// SAME client IP (no X-Forwarded-For spoofing), so all requests share one
// Bucket4j bucket and should start getting HTTP 429 once the 100-request
// budget is exhausted.
//
// Run: k6 run load-testing/k6/rate-limiter-behavior.js

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const okCount = new Counter("responses_200");
const throttledCount = new Counter("responses_429");

// Lightweight local defaults - see product-listing-throughput.js for why.
// Enough requests to exhaust the 100/hour bucket without hammering the
// machine; override via env for a heavier EKS run.
const VUS = parseInt(__ENV.VUS || "3", 10);
const DURATION = __ENV.DURATION || "10s";

export const options = {
  scenarios: {
    single_client_burst: {
      executor: "constant-vus",
      vus: VUS,          // multiple threads, but same source IP -> one bucket
      duration: DURATION,
    },
  },
  thresholds: {
    // We EXPECT some 429s once the bucket is exhausted - the "failure"
    // we actually care about is anything other than 200/429 (crashes, 5xx).
    "responses_429": ["count>0"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/junes/api/v1/product/products/ps5?page=0&size=5`);

  check(res, {
    "status is 200 or 429": (r) => r.status === 200 || r.status === 429,
    "no server errors": (r) => r.status < 500,
  });

  if (res.status === 200) okCount.add(1);
  if (res.status === 429) throttledCount.add(1);

  sleep(0.1);
}
