# Monitoring & Alerting

Prometheus scrapes `junes-app`'s `/actuator/prometheus` endpoint, Grafana visualizes it (`monitoring/grafana/`), and
Alertmanager receives alerts fired by Prometheus rule evaluation (`monitoring/prometheus/rules/`,
`monitoring/alertmanager/`). The same rule file and Alertmanager config are used locally and in production — only the
Prometheus scrape target differs (`prometheus.yml` vs `prometheus.prod.yml`), since in local dev the app runs on the
host while in production it's a compose service.

|              | Local (`docker-compose.yml`) | Production (`docker-compose.app.yml`) |
|--------------|------------------------------|---------------------------------------|
| Prometheus   | `localhost:9090`             | not published — internal only         |
| Grafana      | `localhost:3000`             | `127.0.0.1:3000` — SSH tunnel only    |
| Alertmanager | `localhost:9093`             | not published — internal only         |

In production, reach Grafana/Prometheus/Alertmanager via an SSH tunnel to the host (e.g.
`ssh -L 3000:localhost:3000 <prod-host>`), the same way Kibana is accessed — see the `# Bound to 127.0.0.1` /
`# Not published to host` comments next to each service in `docker-compose.app.yml`.

## Alert rules

Defined in `monitoring/prometheus/rules/alert.rules.yml`. Both are `severity: warning`
and scoped to the `junes-app` Prometheus job.

| Alert            | Condition                                      | Window     | `for` | Rationale                                                                                                                                                                                                                                                                                                                                                                                                    |
|------------------|------------------------------------------------|------------|-------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `HighErrorRate`  | 5xx responses / total responses > **5%**       | rolling 5m | 2m    | 5% is a deliberately loose, illustrative threshold for a demo/local setup rather than a production-tuned SLO — see the ticket's own caveat (MFLP-237). It's well above normal background noise (occasional validation 4xx doesn't count — only 5xx) but low enough to fire from a short scripted burst of errors. `for: 2m` requires the rate to stay elevated rather than firing on a single scrape's blip. |
| `HighP95Latency` | p95 of `http_server_requests_seconds` > **1s** | rolling 5m | 5m    | 1s is generous for typical API responses in this app, so it won't fire under normal local dev load, but is low enough to trip under a modest concurrent load-test. `for: 5m` matches the window length so the condition has to persist across the whole evaluation window, not just spike momentarily.                                                                                                       |

Both thresholds are numbers to demonstrate the mechanism working end-to-end, not values tuned against real production
traffic — revisit them once there's actual production data to base an SLO on.

## Notification channel

Alertmanager is configured with a single `default` receiver with no external webhook/Slack/email target
(`monitoring/alertmanager/alertmanager.yml`). A fired alert is visible directly in Alertmanager's own UI (and under
Prometheus's
`/alerts` tab) — sufficient to demonstrate the alerting pipeline without adding new external dependencies or secrets.
Wiring a real receiver (e.g. Slack webhook)
is a follow-up, not required by MFLP-237.

## Demoing locally

Bring up the stack (`docker compose up prometheus grafana alertmanager ...` plus the app's usual dependencies), run the
app, then:

### Trigger `HighErrorRate`

The app's `GlobalExceptionHandler` maps any unhandled exception to a 500, so the simplest reliable way to generate real
5xx locally is to take out a dependency an endpoint needs, then hammer that endpoint:

```bash
# Stop the DB backing the product catalog
docker compose stop mongodb

# Generate load against a Mongo-backed endpoint for a few minutes
while true; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    http://localhost:8080/junes/api/v1/product/products/pc
  sleep 0.2
done
```

Every request will now 500. Watch it fire at `http://localhost:9090/alerts`
(pending after ~2m, firing shortly after) and `http://localhost:9093`. Restart Mongo (`docker compose start mongodb`)
when done — the alert resolves once the error rate drops back under 5% for a full 5m window.

### Trigger `HighP95Latency`

Drive enough concurrent load that p95 crosses 1s — e.g. with
[`hey`](https://github.com/rakyll/hey) or `ab`, against the (unauthenticated, un-rate-limited) product listing endpoint:

```bash
hey -z 6m -c 50 "http://localhost:8080/junes/api/v1/product/products/pc"
```

(Avoid `/junes/api/v1/product/search` for this — it's rate-limited to 60 requests/min, so concurrent load mostly
produces `429`s rather than real queueing latency.)

If you don't have a load-testing tool installed, a set of parallel bash loops works too, just less precisely:

```bash
for i in $(seq 1 50); do
  ( while true; do curl -s -o /dev/null \
      "http://localhost:8080/junes/api/v1/product/products/pc"; done ) &
done
# remember to `kill %1 %2 ...` (or `kill $(jobs -p)`) when done
```

Watch the same `/alerts` pages as above once p95 has stayed above 1s for 5m.
