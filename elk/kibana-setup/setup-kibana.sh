#!/bin/sh
set -eu

KIBANA_URL="http://kibana:5601"

# In production ELASTIC_PASSWORD is set and Kibana security is enabled
# Locally it's unset and Kibana has no auth, so AUTH_OPTS stays empty
AUTH_OPTS=""
if [ -n "${ELASTIC_PASSWORD:-}" ]; then
  AUTH_OPTS="-u elastic:${ELASTIC_PASSWORD}"
fi

create_or_ignore() {
  desc="$1"; method="$2"; url="$3"; body="$4"
  http_code=$(curl -s -o /tmp/resp.json -w "%{http_code}" $AUTH_OPTS -X "$method" "$url" \
    -H "kbn-xsrf: true" -H "Content-Type: application/json" -d "$body")
  if [ "$http_code" = "200" ] || [ "$http_code" = "409" ]; then
    echo "OK ($http_code): $desc"
  else
    echo "FAILED ($http_code): $desc"
    cat /tmp/resp.json
    exit 1
  fi
}

# 1. Index pattern: junes-logs-*
create_or_ignore "index pattern junes-logs-*" POST \
  "$KIBANA_URL/api/saved_objects/index-pattern/junes-logs-pattern" \
  '{"attributes":{"title":"junes-logs-*","timeFieldName":"@timestamp"}}'

# 2. Saved search: follow a single correlation ID
create_or_ignore "saved search: Logs by Correlation ID" POST \
  "$KIBANA_URL/api/saved_objects/search/junes-logs-by-correlation-id" \
  '{
    "attributes": {
      "title": "Logs by Correlation ID",
      "description": "Edit the query bar with correlationId:\"<value>\" to follow one request/trace end-to-end.",
      "columns": ["correlationId", "level", "logger_name", "message"],
      "sort": [["@timestamp", "desc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"query\":{\"query\":\"correlationId: *\",\"language\":\"kuery\"},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"
      }
    },
    "references": [
      { "name": "kibanaSavedObjectMeta.searchSourceJSON.index", "type": "index-pattern", "id": "junes-logs-pattern" }
    ]
  }'

# 3. Saved search: error/warn logs app-wide
create_or_ignore "saved search: Error & Warn Logs (All Services)" POST \
  "$KIBANA_URL/api/saved_objects/search/junes-logs-error-warn" \
  '{
    "attributes": {
      "title": "Error & Warn Logs (All Services)",
      "description": "All ERROR/WARN log lines app-wide.",
      "columns": ["@timestamp", "level", "logger_name", "correlationId", "message"],
      "sort": [["@timestamp", "desc"]],
      "kibanaSavedObjectMeta": {
        "searchSourceJSON": "{\"query\":{\"query\":\"level: \\\"ERROR\\\" or level: \\\"WARN\\\"\",\"language\":\"kuery\"},\"filter\":[],\"indexRefName\":\"kibanaSavedObjectMeta.searchSourceJSON.index\"}"
      }
    },
    "references": [
      { "name": "kibanaSavedObjectMeta.searchSourceJSON.index", "type": "index-pattern", "id": "junes-logs-pattern" }
    ]
  }'

echo "Kibana provisioning complete."
