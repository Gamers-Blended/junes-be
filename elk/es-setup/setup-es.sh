#!/bin/sh
set -eu

ES_URL="http://elasticsearch:9200"
AUTH="elastic:${ELASTIC_PASSWORD}"

call() {
  desc="$1"; method="$2"; url="$3"; body="$4"
  http_code=$(curl -s -o /tmp/resp.json -w "%{http_code}" -u "$AUTH" -X "$method" "$url" \
    -H "Content-Type: application/json" -d "$body")
  if [ "$http_code" = "200" ] || [ "$http_code" = "201" ]; then
    echo "OK ($http_code): $desc"
  else
    echo "FAILED ($http_code): $desc"
    cat /tmp/resp.json
    exit 1
  fi
}

# Kibana authenticates to Elasticsearch as the built-in kibana_system user
call "set kibana_system password" POST \
  "$ES_URL/_security/user/kibana_system/_password" \
  "{\"password\":\"${KIBANA_SYSTEM_PASSWORD}\"}"

# Logstash writes with a least-privilege user scoped to junes-logs-* only
call "create logstash_writer_role" POST \
  "$ES_URL/_security/role/logstash_writer_role" \
  '{"indices":[{"names":["junes-logs-*"],"privileges":["create_index","write","manage"]}]}'

call "create logstash_writer user" POST \
  "$ES_URL/_security/user/logstash_writer" \
  "{\"password\":\"${LOGSTASH_WRITER_PASSWORD}\",\"roles\":[\"logstash_writer_role\"]}"

echo "Elasticsearch security provisioning complete."
