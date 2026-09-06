// Rate limiter test: POST /junes/api/v1/cart/add
// Goal: prove CartController's @RateLimit(requests = 10, duration = 1 MINUTE,
// perUser = true) correctly throttles a single authenticated account instead
// of letting one abusive session hammer Redis/Postgres unbounded.
//
// Unlike cart-session-throughput.js (one VU = one distinct account, so every
// VU gets its own budget), every request here authenticates as the SAME
// mock account, so all requests share one Bucket4j bucket keyed by user id
// and should start getting HTTP 429 once the 10-request budget is spent.
//
// Prerequisite: at least one mock account must exist, e.g.
//   load-testing/postgres/provision-cart-users.sh 1
// Afterwards, tear down with:
//   load-testing/postgres/cleanup-cart-users.sh
//
// Run: k6 run load-testing/k6/cart-rate-limit-behavior.js

import http from "k6/http";
import { check, sleep, fail } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.CART_TEST_EMAIL || "loadtest_cart_001@example.com";
const PASSWORD = "LoadTest1!";

const okCount = new Counter("responses_200");
const throttledCount = new Counter("responses_429");

// Lightweight local defaults - see product-listing-throughput.js for why.
const VUS = parseInt(__ENV.VUS || "2", 10);
const DURATION = __ENV.DURATION || "10s";

export const options = {
  scenarios: {
    single_account_burst: {
      executor: "constant-vus",
      vus: VUS, // multiple threads, but same account -> one bucket
      duration: DURATION,
    },
  },
  thresholds: {
    // We EXPECT 429s once the bucket is exhausted - the real "failure" we
    // care about is anything other than 200/429 (crashes, 5xx).
    "responses_429": ["count>0"],
  },
};

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/junes/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { "Content-Type": "application/json" } }
  );
  if (loginRes.status !== 200) {
    fail(
      `Could not log in as ${EMAIL} (status ${loginRes.status}). ` +
      `Run: load-testing/postgres/provision-cart-users.sh 1`
    );
  }
  const token = JSON.parse(loginRes.body).token;

  // Spoofed IP so this internal product lookup doesn't compete with
  // ProductController's per-IP bucket (100/hour) - that limiter isn't what
  // this test is exercising, and sequential local runs share one real IP.
  const productRes = http.get(`${BASE_URL}/junes/api/v1/product/products/ps5?page=0&size=1`, {
    headers: { "X-Forwarded-For": "10.99.99.1" },
  });
  const productId = JSON.parse(productRes.body).content[0].productID;

  return { token, productId };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/junes/api/v1/cart/add`,
    JSON.stringify({ productID: data.productId, price: 59.99, quantity: 1 }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        "Content-Type": "application/json",
      },
    }
  );

  check(res, {
    "status is 200 or 429": (r) => r.status === 200 || r.status === 429,
    "no server errors": (r) => r.status < 500,
  });

  if (res.status === 200) okCount.add(1);
  if (res.status === 429) throttledCount.add(1);

  sleep(0.1);
}
