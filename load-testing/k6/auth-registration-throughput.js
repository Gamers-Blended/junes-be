// Load test: concurrent auth traffic
//   POST /junes/api/v1/auth/add-user  (registration)
//   POST /junes/api/v1/auth/login
//
// Goal: show the app handles concurrent registrations and logins without
// falling over, specifically because both are CPU-bound in a way the
// other endpoints aren't - Argon2PasswordEncoder hashes (registration) or
// verifies (login) a password on every single call, deliberately slow by
// design (that's what makes Argon2 resistant to offline cracking). This
// is the test to point at for "handles concurrent load on a CPU-heavy
// path," as opposed to the I/O-bound Redis/Postgres/Mongo paths the other
// scripts exercise.
//
// Registration additionally fans out through RabbitMQ
// (EmailProducerService -> email.queue, for the verification email) -
// see AuthService.addUser / EmailDeliveryException. If RabbitMQ isn't
// running locally this endpoint 500s (a real, hard synchronous dependency -
// not a bug in this test); bring it up with
// `docker compose up -d rabbitmq` alongside postgres/mongodb/redis.
//
// Two independent scenarios run concurrently (both start at t=0):
//   registrations - brand-new, never-verified accounts (own emails, own
//     X-Session-Id so they don't share AuthController's per-session/IP
//     rate-limit bucket). Exercises Argon2 hash + RabbitMQ enqueue.
//   logins - repeated logins against already-provisioned, already-verified
//     accounts. Exercises Argon2 verify + JWT issuance. Reuses the same
//     mock-account pool as the cart tests (any provisioned, verified
//     account works - login doesn't touch cart/order data).
//
// Prerequisites:
//   load-testing/postgres/provision-cart-users.sh 2   (for the logins scenario)
// Afterwards, tear down the accounts THIS script created:
//   load-testing/postgres/cleanup-auth-reg-users.sh
//   load-testing/postgres/cleanup-cart-users.sh
//
// Run: k6 run load-testing/k6/auth-registration-throughput.js
// (LOGIN_USERS must be <= the count passed to provision-cart-users.sh; for
// a heavier EKS run bump REG_USERS/LOGIN_USERS and provision-cart-users.sh's count together)

import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
// Lightweight local defaults - see product-listing-throughput.js for why.
const REG_USERS = parseInt(__ENV.REG_USERS || "2", 10);
const LOGIN_USERS = parseInt(__ENV.LOGIN_USERS || "2", 10);
const REG_EMAIL_PREFIX = "loadtest_authreg_";
const CART_EMAIL_PREFIX = "loadtest_cart_"; // pre-provisioned, verified pool
const EMAIL_DOMAIN = "example.com";
const PASSWORD = "LoadTest1!";

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const options = {
  scenarios: {
    registrations: {
      executor: "per-vu-iterations",
      exec: "register",
      vus: REG_USERS,
      iterations: 1,
      maxDuration: "1m",
    },
    logins: {
      executor: "per-vu-iterations",
      exec: "login",
      vus: LOGIN_USERS,
      iterations: 1,
      maxDuration: "1m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    // Argon2 is intentionally slow (that's the point of it) - this
    // threshold is generous relative to product-listing/cart because
    // every request here does a real hash or verify, not a cache/index hit.
    http_req_duration: ["p(95)<2000"],
    checks: ["rate>0.99"],
  },
};

export function register() {
  const email = `${REG_EMAIL_PREFIX}${uuidv4()}@${EMAIL_DOMAIN}`;
  const res = http.post(
    `${BASE_URL}/junes/api/v1/auth/add-user`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json", "X-Session-Id": uuidv4() } }
  );
  check(res, { "register: 200": (r) => r.status === 200 });
}

export function login() {
  const i = ((__VU - 1) % LOGIN_USERS) + 1;
  const email = `${CART_EMAIL_PREFIX}${String(i).padStart(3, "0")}@${EMAIL_DOMAIN}`;
  const res = http.post(
    `${BASE_URL}/junes/api/v1/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: { "Content-Type": "application/json", "X-Session-Id": uuidv4() } }
  );
  check(res, {
    "login: 200": (r) => r.status === 200,
    "login: token present": (r) => {
      try {
        return JSON.parse(r.body).token.length > 0;
      } catch (e) {
        return false;
      }
    },
  });
}
