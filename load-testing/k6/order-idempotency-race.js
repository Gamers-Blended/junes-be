// Correctness test: POST /junes/api/v1/order/place under a duplicate-request
// race, all sharing the SAME Idempotency-Key.
//
// Goal: prove IdempotentUtils's (userID, eventType, keyValue) mechanism
// actually prevents a double-charge/double-order when a client retries a
// checkout (flaky network, impatient double-click, buggy retry logic) -
// not a throughput test, a race-condition correctness test. This is a much
// stronger resume claim than raw RPS: "verified exactly-once order
// creation under N concurrent duplicate submissions."
//
// Expected behaviour per IdempotentUtils.java: the first request to win
// the DB insert runs the real order-creation logic; concurrent duplicates
// that arrive while it's still IN_PROGRESS get HTTP 409; duplicates that
// arrive after it COMPLETED get back the *cached* HTTP 200 response byte-
// for-byte (same order number) instead of creating a second order. So
// every 200 response in the burst must carry the identical order number -
// that's the actual invariant this test checks, not "everyone gets 200."
//
// RACE_REQUESTS is deliberately small (default 5, cap suggested at 8): all
// of it burns the same OrderController per-user budget (10 req/min - see
// OrderController's class-level @RateLimit), and going higher just adds
// 429 noise without exercising the race any harder.
//
// Prerequisite: at least one mock account must exist, e.g.
//   load-testing/postgres/provision-order-users.sh 1
// Afterwards, tear down with:
//   load-testing/postgres/cleanup-order-users.sh
//
// Run: k6 run load-testing/k6/order-idempotency-race.js

import http from "k6/http";
import { check, fail } from "k6";
import { Counter } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const EMAIL = __ENV.ORDER_TEST_EMAIL || "loadtest_order_001@example.com";
const PASSWORD = "LoadTest1!";
const RACE_REQUESTS = parseInt(__ENV.RACE_REQUESTS || "5", 10);

const completedCount = new Counter("responses_200");
const inProgressCount = new Counter("responses_409");
const throttledCount = new Counter("responses_429");

export const options = {
  scenarios: {
    duplicate_checkout_burst: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
    },
  },
  thresholds: {
    checks: ["rate>0.99"],
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
      `Run: load-testing/postgres/provision-order-users.sh 1`
    );
  }
  const token = JSON.parse(loginRes.body).token;

  const addressRes = http.get(`${BASE_URL}/junes/api/v1/saved-items/addresses/user`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const paymentRes = http.get(`${BASE_URL}/junes/api/v1/saved-items/payment-methods/user`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const addresses = JSON.parse(addressRes.body);
  const paymentMethods = JSON.parse(paymentRes.body);
  if (!addresses.length || !paymentMethods.length) {
    fail(`${EMAIL} has no saved address/payment method - run provision-order-users.sh first.`);
  }

  // Spoofed IP so this internal product lookup doesn't compete with
  // ProductController's per-IP bucket (100/hour) - that limiter isn't what
  // this test is exercising, and sequential local runs share one real IP.
  const productRes = http.get(`${BASE_URL}/junes/api/v1/product/products/ps5?page=0&size=1`, {
    headers: { "X-Forwarded-For": "10.99.99.1" },
  });
  const productId = JSON.parse(productRes.body).content[0].productID;

  return {
    token,
    addressID: addresses[0].addressID,
    paymentMethodID: paymentMethods[0].paymentMethodID,
    productId,
  };
}

export default function (data) {
  const idempotencyKey = `race-${Date.now()}`;
  const headers = {
    Authorization: `Bearer ${data.token}`,
    "Content-Type": "application/json",
    "Idempotency-Key": idempotencyKey,
  };
  const body = JSON.stringify({
    addressDTO: { addressID: data.addressID },
    paymentMethodID: data.paymentMethodID,
    orderItemDTOList: [{ productID: data.productId, quantity: 1 }],
    shippingCost: 5.99,
  });

  // http.batch dispatches all of these over the network concurrently from
  // this single VU/iteration - the actual "N clients double-clicking
  // checkout at once" scenario, all racing on the same idempotency key.
  const requests = Array.from({ length: RACE_REQUESTS }, () => ["POST", `${BASE_URL}/junes/api/v1/order/place`, body, { headers }]);
  const responses = http.batch(requests);

  const orderNumbers = new Set();
  responses.forEach((res) => {
    check(res, { "status is 200, 409 or 429": (r) => [200, 409, 429].includes(r.status) });
    if (res.status === 200) {
      completedCount.add(1);
      orderNumbers.add(JSON.parse(res.body).message);
    } else if (res.status === 409) {
      inProgressCount.add(1);
    } else if (res.status === 429) {
      throttledCount.add(1);
    }
  });

  check(orderNumbers, {
    "every completed response shares the same order number": (s) => s.size <= 1,
  });
  if (orderNumbers.size > 1) {
    console.error(`Idempotency violation: ${orderNumbers.size} distinct order numbers from one key: ${[...orderNumbers]}`);
  }

  // A final request after the burst has settled should hit the cached
  // COMPLETED row and replay the same order number - proving the
  // idempotency cache, not just request-in-flight blocking, is what's
  // preventing duplicates.
  const replayRes = http.post(`${BASE_URL}/junes/api/v1/order/place`, body, { headers });
  check(replayRes, {
    "post-burst replay returns 200": (r) => r.status === 200,
    "post-burst replay matches the winning order number": (r) => {
      if (orderNumbers.size === 0) return true; // burst was fully rate-limited; nothing to compare
      try {
        return orderNumbers.has(JSON.parse(r.body).message);
      } catch (e) {
        return false;
      }
    },
  });
}
