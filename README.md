# Junes Online Video Game Store

Back End service for Junes prototype online video game store.

This is a dummy online website that sells video games.

## Prerequisites
```
docker
docker compose
```

## Docker
Ensure that terminal is in project directory (where `docker-compose.yml` is located) <br>
To run container, run:
```
docker compose up 
```

To stop container:
```
docker compose down
```

## MongoDB Compass
In terminal, run this command:
```
mongodb-compass
```
Manage product documents on with this GUI.

## PgAdmin
Open this URL in a browser
```
localhost:5050
```
Login with the credentials stated inside `docker-compose.yml`. <br>
Manage the user and cartItems database here.

## Elasticsearch, Logstash & Kibana (ELK)
```
localhost:9200   # Elasticsearch
localhost:5601   # Kibana
```
App logs are shipped automatically to Logstash (`localhost:5044`) once the stack is up — no manual steps needed, no login required (security disabled for local dev). Open Kibana and use **Discover** to browse the `junes-logs-*` index (pre-provisioned automatically), or open one of the two pre-provisioned saved searches from **Discover > Open**:
- `Logs by Correlation ID` — edit the query bar with `correlationId: "<value>"` to follow one request/trace end-to-end.
- `Error & Warn Logs (All Services)` — all `ERROR`/`WARN` logs app-wide.

If Elasticsearch fails to start with a `vm.max_map_count` error, run on the host (Linux only): `sudo sysctl -w vm.max_map_count=262144`.

## Prometheus & Grafana
```
localhost:9090   # Prometheus
localhost:3000   # Grafana
```
Prometheus scrapes the app's `/actuator/prometheus` endpoint on `localhost:9404` every 15s — **the app must be running locally** (`./mvnw spring-boot:run` or your IDE run configuration) for Prometheus to have any data, since the local `docker-compose.yml` does not run the app itself. Check **Status > Targets** in the Prometheus UI to confirm the `junes-app` job is `UP`.

Open Grafana and log in with `admin` / `admin` (local dev only). The Prometheus data source and the three dashboards (**API Latency & Error Rate**, **JVM Health**, **Kafka Consumer Lag**) under the **Junes** folder are provisioned automatically on startup — no manual setup, and it survives `docker compose down` + `up` since everything lives under `monitoring/` in the repo.

## Jenkins UI
```
localhost:8090/jenkins
```
