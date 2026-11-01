param(
  [string]$Namespace = "vellumhub",
  [string]$GatewayUrl = "http://vellumhub.local",
  [string]$OutputPath = "target/metrics/kubernetes.json"
)

$ErrorActionPreference = "Stop"
$services = @("auth-service", "gateway-service", "user-service", "catalog-service", "engagement-service", "recommendation-service")
$results = @()
$passed = $false
try {
  foreach ($service in $services) {
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    kubectl rollout status "deployment/$service" -n $Namespace --timeout=10m
    if ($LASTEXITCODE -ne 0) { throw "Rollout failed for $service" }
    $timer.Stop()
    $ready = kubectl get deployment $service -n $Namespace -o jsonpath='{.status.readyReplicas}'
    if ($LASTEXITCODE -ne 0 -or -not $ready -or [int]$ready -lt 1) {
      throw "Deployment $service has no ready replicas"
    }
    $results += [ordered]@{ service = $service; ready_replicas = [int]$ready; rollout_observation_ms = $timer.ElapsedMilliseconds }
  }
  & "$PSScriptRoot/smoke-kubernetes.ps1" -Namespace $Namespace -GatewayUrl $GatewayUrl
  if (-not $?) { throw "Kubernetes smoke failed" }
  $passed = $true
} finally {
  $record = [ordered]@{
    schema_version = 1
    metric_id = "M18"
    commit_sha = (git rev-parse HEAD 2>$null)
    timestamp_utc = (Get-Date).ToUniversalTime().ToString("o")
    environment = "user-supplied Kubernetes context"
    context = (kubectl config current-context 2>$null)
    namespace = $Namespace
    deployment_results = $results
    expected_deployments = $services.Count
    passed = $passed
    limitations = "Observation of existing rollout readiness and smoke, not deployment duration, rollback or production uptime"
  }
  $folder = Split-Path -Parent $OutputPath
  if ($folder) { New-Item -ItemType Directory -Force -Path $folder | Out-Null }
  $record | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 $OutputPath
  Write-Host "M18 evidence written: $OutputPath"
}
if (-not $passed) { exit 1 }
