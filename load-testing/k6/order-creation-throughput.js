// Load test: authenticated checkout flow
//   POST /junes/api/v1/order/place
//
// Goal: show the app can place many concurrent, independent orders
// correctly - exercising Postgres (address/payment-method lookups,
// atomic inventory reservation), Mongo (per-product stock decrement via
// InventoryService.reserveStock), and the transactional-outbox write
// (Transaction + OutboxEvent in one DB transaction) that
// OutboxRelay/Kafka pick up afterwards. This is the "main order workflow"
// the app is actually built around - product listing/cart don't touch
// the outbox at all.
//
// Every order request never talks to Stripe directly (see
// OrderController/OrderProcessingService) - the charge happens later,
// async, off a Kafka consumer - so this test measures the HTTP/DB path
// only, same scope as provision-order-users.sh's stub payment methods.
//
// Like cart-session-throughput.js, OrderController's rate limit is
// per-user (10/min), so this uses a 1-VU-per-account, 1-iteration-per-VU
// shape: every virtual user IS a distinct real account with its own JWT,
// address and payment method, proving concurrency/correctness across N
// independent checkouts rather than sustained throughput against one
// shared budget (see order-idempotency-race.js for a single account under
// concurrent duplicate requests instead).
//
// Prerequisite: provision the mock accounts first (see load-testing/postgres/):
//   load-testing/postgres/provision-order-users.sh 2
// Afterwards, tear them down with:
//   load-testing/postgres/cleanup-order-users.sh
//
// Run: k6 run load-testing/k6/order-creation-throughput.js
// (ORDER_USERS must be <= the count passed to provision-order-users.sh; for
// a heavier EKS run bump both, e.g. provision-order-users.sh 150 &&
// ORDER_USERS=150 k6 run ...)

import http from "k6/http";
import { check, sleep, fail } from "k6";
import { Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
// Default is a lightweight local smoke test (correctness, not throughput) -
// see product-listing-throughput.js for why. Override for a heavier EKS
// run, e.g. ORDER_USERS=150, and provision that many users first.
const ORDER_USERS = parseInt(__ENV.ORDER_USERS || "2", 10);
const EMAIL_PREFIX = "loadtest_order_";
const EMAIL_DOMAIN = "example.com";
const PASSWORD = "LoadTest1!";
const PLATFORMS_FOR_CATALOG = ["ps5", "ps4", "xsx", "nsw", "pc"];

const orderDuration = new Trend("order_place_duration", true);

// Same per-VU session id trick as cart-session-throughput.js - see there
// for why (AuthController's unauthenticated rate limit falls back to
// X-Session-Id, then IP, and every VU here logs in from the same k6 host).
function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const options = {
  scenarios: {
    concurrent_checkouts: {
      executor: "per-vu-iterations",
      vus: ORDER_USERS,
      iterations: 1,
      maxDuration: "2m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1500"],
    checks: ["rate>0.99"],
  },
};

// Runs once, single-threaded, before any VU starts.
export function setup() {
  // Pull a small pool of real product ids to order, instead of hardcoding
  // Mongo ObjectIds that could go stale if the catalog changes. Spoofed IP
  // so this internal lookup doesn't compete with ProductController's
  // per-IP bucket (100/hour) - not what this test exercises.
  const productIds = [];
  for (const platform of PLATFORMS_FOR_CATALOG) {
    const res = http.get(`${BASE_URL}/junes/api/v1/product/products/${platform}?page=0&size=10`, {
      headers: { "X-Forwarded-For": "10.99.99.1" },
    });
    if (res.status === 200) {
      const body = JSON.parse(res.body);
      for (const p of body.content || []) productIds.push(p.productID);
    }
  }
  if (productIds.length === 0) {
    fail("No products found to order - seed the product catalog first.");
  }

  // Log in every mock account in one batched round-trip.
  const loginRequests = [];
  for (let i = 1; i <= ORDER_USERS; i++) {
    const email = `${EMAIL_PREFIX}${String(i).padStart(3, "0")}@${EMAIL_DOMAIN}`;
    loginRequests.push([
      "POST",
      `${BASE_URL}/junes/api/v1/auth/login`,
      JSON.stringify({ email, password: PASSWORD }),
      { headers: { "Content-Type": "application/json", "X-Session-Id": uuidv4() } },
    ]);
  }
  const loginResponses = http.batch(loginRequests);

  const tokens = [];
  loginResponses.forEach((res, idx) => {
    if (res.status === 200) {
      tokens.push(JSON.parse(res.body).token);
    } else {
      console.warn(`login #${idx + 1} failed with status ${res.status}: ${res.body}`);
    }
  });

  if (tokens.length < ORDER_USERS) {
    fail(
      `Only ${tokens.length}/${ORDER_USERS} mock accounts logged in. ` +
      `Run: load-testing/postgres/provision-order-users.sh ${ORDER_USERS}`
    );
  }

  // Fetch each account's seeded address + payment method id (created by
  // provision-order-users.sh directly in Postgres, so we don't know the
  // generated UUIDs up front) in two more batched round-trips.
  const addressRequests = tokens.map((token) => [
    "GET",
    `${BASE_URL}/junes/api/v1/saved-items/addresses/user`,
    null,
    { headers: { Authorization: `Bearer ${token}` } },
  ]);
  const paymentRequests = tokens.map((token) => [
    "GET",
    `${BASE_URL}/junes/api/v1/saved-items/payment-methods/user`,
    null,
    { headers: { Authorization: `Bearer ${token}` } },
  ]);
  const addressResponses = http.batch(addressRequests);
  const paymentResponses = http.batch(paymentRequests);

  const users = [];
  tokens.forEach((token, idx) => {
    const addresses = addressResponses[idx].status === 200 ? JSON.parse(addressResponses[idx].body) : [];
    const paymentMethods = paymentResponses[idx].status === 200 ? JSON.parse(paymentResponses[idx].body) : [];
    if (addresses.length > 0 && paymentMethods.length > 0) {
      users.push({
        token,
        addressID: addresses[0].addressID,
        paymentMethodID: paymentMethods[0].paymentMethodID,
      });
    }
  });

  if (users.length < ORDER_USERS) {
    fail(
      `Only ${users.length}/${ORDER_USERS} accounts have a saved address + payment method. ` +
      `Run: load-testing/postgres/provision-order-users.sh ${ORDER_USERS}`
    );
  }

  return { users, productIds };
}

export default function (data) {
  const user = data.users[(__VU - 1) % data.users.length];
  const headers = {
    Authorization: `Bearer ${user.token}`,
    "Content-Type": "application/json",
    "Idempotency-Key": uuidv4(),
  };

  const start = Date.now();

  const orderItemDTOList = [
    { productID: data.productIds[Math.floor(Math.random() * data.productIds.length)], quantity: 1 },
  ];

  const res = http.post(
    `${BASE_URL}/junes/api/v1/order/place`,
    JSON.stringify({
      addressDTO: { addressID: user.addressID },
      paymentMethodID: user.paymentMethodID,
      orderItemDTOList,
      shippingCost: 5.99,
    }),
    { headers }
  );

  const ok = check(res, {
    "place order: 200": (r) => r.status === 200,
    "response has order number": (r) => {
      try {
        return JSON.parse(r.body).message.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
  // 500 here most often means InsufficientStockException - a seeded
  // product's random stock (see mongo/seed-products.js) ran out under
  // concurrent load, not a bug in the app. Re-seed and rerun if this fires.
  if (!ok) console.warn(`VU ${__VU}: order placement failed, status ${res.status}: ${res.body}`);

  orderDuration.add(Date.now() - start);
  sleep(0.2);
}
