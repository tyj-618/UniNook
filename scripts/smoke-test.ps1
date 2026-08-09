[CmdletBinding()]
param(
    [string]$WebBaseUrl = "http://127.0.0.1:8088",
    [string]$ApplicationBaseUrl = "http://127.0.0.1:8080",
    [ValidateRange(5, 120)]
    [int]$TimeoutSeconds = 45
)

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$healthUrl = "$($ApplicationBaseUrl.TrimEnd('/'))/actuator/health"

do {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5
        if ($health.status -eq 'UP') {
            break
        }
    } catch {
        # The application may still be starting while dependencies become ready.
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

if ($null -eq $health -or $health.status -ne 'UP') {
    throw "Application health check did not report UP within $TimeoutSeconds seconds: $healthUrl"
}

$webResponse = Invoke-WebRequest -Uri $WebBaseUrl.TrimEnd('/') -UseBasicParsing -TimeoutSec 10
if ($webResponse.StatusCode -ne 200) {
    throw "Web entry returned HTTP $($webResponse.StatusCode): $WebBaseUrl"
}

Write-Host "Smoke test passed. Application: $healthUrl; Web: $WebBaseUrl"
