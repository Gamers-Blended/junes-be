// Load test: authenticated cart flow
//   GET  /junes/api/v1/cart/products
//   POST /junes/api/v1/cart/add
//   PUT  /junes/api/v1/cart/{productID}/quantity
//   DELETE /junes/api/v1/cart/items
//
// Goal: show the app can serve many concurrent, independent, authenticated
// shopping sessions correctly and quickly - exercising Redis (RedisCartRepository,
// the Lua-scripted optimistic-locking cart update), Postgres (login), and the
// per-user Bucket4j rate limiter on CartController (10 req/min/user) all at once.
//
// CartController's rate limit is keyed PER USER, not per IP, so unlike the
// product-listing test there's no header to spoof - instead every virtual
// user IS a distinct real user (its own JWT from a real /login call), so
// each VU's session naturally lands in its own bucket. That's why this test
// uses a 1-VU-per-account, 1-iteration-per-VU shape instead of a ramp: it
// proves concurrency and correctness across N independent identities rather
// than sustained throughput against a single shared budget (see
// cart-rate-limit-behavior.js for the latter).
//
// Prerequisite: provision the mock accounts first (see load-testing/postgres/):
//   load-testing/postgres/provision-cart-users.sh 2
// Afterwards, tear them down with:
//   load-testing/postgres/cleanup-cart-users.sh
//
// Run: k6 run load-testing/k6/cart-session-throughput.js
// (CART_USERS must be <= the count passed to provision-cart-users.sh; for a
// heavier EKS run bump both, e.g. provision-cart-users.sh 150 && CART_USERS=150 k6 run ...)

import http from "k6/http";
import { check, sleep, fail } from "k6";
import { Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
// Default is a lightweight local smoke test (correctness, not throughput) -
// see product-listing-throughput.js for why. Override for a heavier EKS run,
// e.g. CART_USERS=150, and provision that many users first.
const CART_USERS = parseInt(__ENV.CART_USERS || "2", 10);
const EMAIL_PREFIX = "loadtest_cart_";
const EMAIL_DOMAIN = "example.com";
const PASSWORD = "LoadTest1!";
const PLATFORMS_FOR_CATALOG = ["ps5", "ps4", "xsx", "nsw", "pc"];

const sessionDuration = new Trend("cart_session_duration", true);

// AuthController's class-level @RateLimit(perUser = true) keys unauthenticated
// requests (like /login, before we have a JWT) by the X-Session-Id header if
// present, falling back to client IP otherwise. Since every VU here calls
// /login from the same k6 host, omitting X-Session-Id would put all 150
// logins in one shared IP bucket and throttle after 10. Giving each VU its
// own session id mirrors how distinct real browser sessions behind the same
// NAT/office IP each get their own bucket.
function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const options = {
  scenarios: {
    concurrent_shoppers: {
      executor: "per-vu-iterations",
      vus: CART_USERS,
      iterations: 1,
      maxDuration: "2m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<800"],
    checks: ["rate>0.99"],
  },
};

// Runs once, single-threaded, before any VU starts.
export function setup() {
  // Pull a small pool of real product ids to add to cart, instead of
  // hardcoding Mongo ObjectIds that could go stale if the catalog changes.
  // Spoofed IP so this internal lookup doesn't compete with ProductController's
  // per-IP bucket (100/hour) - that limiter isn't what this test exercises,
  // and sequential local runs share one real IP.
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
    fail("No products found to add to cart - seed the product catalog first.");
  }

  // Log in every mock account in one batched round-trip.
  const loginRequests = [];
  for (let i = 1; i <= CART_USERS; i++) {
    const email = `${EMAIL_PREFIX}${String(i).padStart(3, "0")}@${EMAIL_DOMAIN}`;
    loginRequests.push([
      "POST",
      `${BASE_URL}/junes/api/v1/auth/login`,
      JSON.stringify({ email, password: PASSWORD }),
      { headers: { "Content-Type": "application/json", "X-Session-Id": uuidv4() } },
    ]);
  }
  const responses = http.batch(loginRequests);

  const tokens = [];
  responses.forEach((res, idx) => {
    if (res.status === 200) {
      tokens.push(JSON.parse(res.body).token);
    } else {
      console.warn(`login #${idx + 1} failed with status ${res.status}: ${res.body}`);
    }
  });

  if (tokens.length < CART_USERS) {
    fail(
      `Only ${tokens.length}/${CART_USERS} mock accounts logged in. ` +
      `Run: load-testing/postgres/provision-cart-users.sh ${CART_USERS}`
    );
  }

  return { tokens, productIds };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const headers = {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };

  const start = Date.now();

  // 1. View empty cart
  let res = http.get(`${BASE_URL}/junes/api/v1/cart/products`, { headers });
  check(res, { "view cart: 200": (r) => r.status === 200 });
  sleep(0.2);

  // 2. Add two distinct products
  const picks = [
    data.productIds[Math.floor(Math.random() * data.productIds.length)],
    data.productIds[Math.floor(Math.random() * data.productIds.length)],
  ];
  for (const productID of picks) {
    res = http.post(
      `${BASE_URL}/junes/api/v1/cart/add`,
      JSON.stringify({ productID, price: 59.99, quantity: 1 }),
      { headers }
    );
    check(res, { "add to cart: 200": (r) => r.status === 200 });
    sleep(0.2);
  }

  // 3. Update quantity on the first item added
  res = http.put(
    `${BASE_URL}/junes/api/v1/cart/${picks[0]}/quantity?quantity=3`,
    null,
    { headers }
  );
  check(res, { "update quantity: 200": (r) => r.status === 200 });
  sleep(0.2);

  // 4. View updated cart, confirm both items are actually there
  res = http.get(`${BASE_URL}/junes/api/v1/cart/products`, { headers });
  const cartOk = check(res, {
    "view updated cart: 200": (r) => r.status === 200,
    "cart has both items": (r) => {
      try {
        return JSON.parse(r.body).content.length === 2;
      } catch (e) {
        return false;
      }
    },
  });
  if (!cartOk) console.warn(`VU ${__VU}: cart contents mismatch`);

  // 5. Clear cart (leaves state clean for a rerun without needing cleanup)
  res = http.del(`${BASE_URL}/junes/api/v1/cart/items`, null, { headers });
  check(res, { "clear cart: 200": (r) => r.status === 200 });

  sessionDuration.add(Date.now() - start);
}
