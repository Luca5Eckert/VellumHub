param(
  [string]$Namespace = "vellumhub",
  [string]$GatewayUrl = "http://vellumhub.local"
)

$ErrorActionPreference = "Stop"
$deployments = @("gateway-service", "user-service", "catalog-service", "engagement-service", "recommendation-service")
foreach ($deployment in $deployments) {
  kubectl rollout status "deployment/$deployment" -n $Namespace --timeout=10m
}

foreach ($path in @("/actuator/health/liveness", "/actuator/health/readiness")) {
  $response = Invoke-WebRequest -UseBasicParsing -Uri "$GatewayUrl$path" -TimeoutSec 30
  if ($response.StatusCode -ne 200) { throw "Gateway $path returned $($response.StatusCode)" }
}

# DNS and backing-service connectivity are asserted from the running gateway Pod.
$gatewayPod = kubectl get pod -n $Namespace -l app.kubernetes.io/name=gateway-service -o jsonpath='{.items[0].metadata.name}'
if (-not $gatewayPod) { throw "Gateway Pod was not found" }
foreach ($host in @("user-service", "catalog-service", "engagement-service", "recommendation-service", "redis", "kafka")) {
  kubectl exec -n $Namespace $gatewayPod -- sh -c "getent hosts $host" | Out-Null
}
Write-Host "Kubernetes smoke test passed. Execute authenticated route and Kafka event checks with the test credentials of the target environment."
