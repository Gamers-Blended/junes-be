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

## Jenkins UI
```
localhost:8090/jenkins
```
