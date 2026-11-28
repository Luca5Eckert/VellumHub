#!/usr/bin/env bash
# Exercise the routing client with Docker DNS and immediate IP reuse in an isolated network.
set -euo pipefail

image=${1:?Usage: verify-gateway-upstream-remap.sh LOCAL_GATEWAY_IMAGE}
prefix="vh-upstream-remap-$$"
network="$prefix"
gateway="$prefix-gateway"
user="$prefix-user"
other="$prefix-other"
fixture="$(mktemp)"
trap 'docker rm -f "$gateway" "$user" "$other" >/dev/null 2>&1 || true; docker network rm "$network" >/dev/null 2>&1 || true; rm -f "$fixture"' EXIT

cat >"$fixture" <<'HTTP_SERVER'
#!/bin/sh
cr="$(printf '\r')"
while IFS= read -r line; do
  [ "$line" = "$cr" ] && break
done
printf 'HTTP/1.1 200 OK\r\nContent-Length: %s\r\nConnection: close\r\n\r\n%s' "${#BACKEND}" "$BACKEND"
HTTP_SERVER
chmod 755 "$fixture"

# The subnet can be overridden if the host already uses the default /24.
subnet_prefix=${VELLUM_REMAP_SUBNET_PREFIX:-172.30.203}
docker network create --subnet "$subnet_prefix.0/24" "$network" >/dev/null
old_ip="$subnet_prefix.10"
new_ip="$subnet_prefix.11"
docker run -d --name "$user" --network "$network" --ip "$old_ip" --network-alias user-service \
    -e BACKEND=USER -v "$fixture:/fixture.sh:ro" redis:7.4-alpine nc -lk -p 8080 -e /fixture.sh >/dev/null
docker run -d --name "$other" --network "$network" --ip "$subnet_prefix.12" \
    -e BACKEND=OTHER -v "$fixture:/fixture.sh:ro" redis:7.4-alpine nc -lk -p 8080 -e /fixture.sh >/dev/null
docker run -d --name "$gateway" --network "$network" -p 127.0.0.1::8080 \
    -e VELLUM_DOCS_ENABLED=true -e JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -Xmx384m' "$image" >/dev/null

url="http://$(docker port "$gateway" 8080/tcp)/docs/user/v3/api-docs"
for attempt in $(seq 1 90); do
    response="$(curl --noproxy '*' -fs --max-time 2 "$url" || true)"
    [[ "$response" != USER ]] || break
    sleep 1
done
if [[ "$response" != USER ]]; then
    echo "Gateway did not reach the initial upstream" >&2
    exit 1
fi

started_at="$(docker inspect --format '{{.State.StartedAt}}' "$gateway")"
docker network disconnect "$network" "$user"
docker network disconnect "$network" "$other"
docker network connect --ip "$old_ip" "$network" "$other"
docker network connect --ip "$new_ip" --alias user-service "$network" "$user"

response="$(curl --noproxy '*' -fsS --max-time 5 "$url")"
if [[ "$response" != USER || "$started_at" != "$(docker inspect --format '{{.State.StartedAt}}' "$gateway")" ]]; then
    echo "Gateway did not reach the original upstream at its new address without restarting" >&2
    exit 1
fi
echo "Gateway reached the original upstream at its new address without restarting"
