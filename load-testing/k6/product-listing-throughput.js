// Load test: GET /junes/api/v1/product/products/{platform}
// Goal: measure how many concurrent "shoppers" the app + Mongo + Redis stack
// can serve locally, independent of the per-IP rate limiter.
//
// The ProductController rate limit (100 req/hour) is keyed by client IP,
// and RateLimitAspect.getClientIpAddress() trusts X-Forwarded-For. Each
// virtual user here sends a distinct synthetic IP via that header, the same
// way distinct real shoppers behind a load balancer would each get their
// own IP — so this measures raw throughput headroom rather than the
// rate limiter's rejection path (see rate-limiter-behavior.js for that).
//
// Run: k6 run load-testing/k6/product-listing-throughput.js
// HTML/JSON summary: k6 run --summary-export=summary.json load-testing/k6/product-listing-throughput.js

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

const PLATFORMS = ["ps5", "ps4", "xsx", "xbo", "nsw", "pc"];
const GENRES = ["Action", "RPG", "Shooter", "Platformer", "Sports"];

const errorRate = new Rate("errors");
const listingDuration = new Trend("product_listing_duration", true);

// Defaults are a lightweight local smoke test (correctness, not throughput) -
// this machine also runs Kafka/ES/Kibana/Grafana/Postgres/Mongo/Redis/RabbitMQ
// alongside the app, so a real concurrency run would starve everything else.
// Real throughput numbers belong on the EKS load-test cluster: override via
// env, e.g. PEAK_VUS=200 k6 run load-testing/k6/product-listing-throughput.js
const PEAK_VUS = parseInt(__ENV.PEAK_VUS || "5", 10);

export const options = {
  scenarios: {
    ramping_shoppers: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "5s", target: Math.ceil(PEAK_VUS / 2) },  // warm up
        { duration: "5s", target: PEAK_VUS },                 // ramp to peak
        { duration: "10s", target: PEAK_VUS },                // hold at peak
        { duration: "5s", target: 0 },                        // ramp down
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],       // <1% errors
    http_req_duration: ["p(95)<800", "p(99)<1500"],
    checks: ["rate>0.99"],
  },
};

function syntheticIp(vu, iter) {
  const a = 10;
  const b = (vu >> 8) & 0xff;
  const c = vu & 0xff;
  const d = iter % 256;
  return `${a}.${b}.${c}.${d}`;
}

export default function () {
  const platform = PLATFORMS[Math.floor(Math.random() * PLATFORMS.length)];
  const genre = GENRES[Math.floor(Math.random() * GENRES.length)];
  const page = Math.floor(Math.random() * 5);

  const url = `${BASE_URL}/junes/api/v1/product/products/${platform}` +
    `?page=${page}&size=20&genres=${genre}`;

  const params = {
    headers: {
      "X-Forwarded-For": syntheticIp(__VU, __ITER),
    },
    tags: { endpoint: "product_listing" },
  };

  const res = http.get(url, params);

  const ok = check(res, {
    "status is 200": (r) => r.status === 200,
    "body has content field": (r) => {
      try {
        return Array.isArray(JSON.parse(r.body).content);
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(!ok);
  listingDuration.add(res.timings.duration);

  sleep(Math.random() * 0.5); // think time, avoids unrealistic hammering
}
