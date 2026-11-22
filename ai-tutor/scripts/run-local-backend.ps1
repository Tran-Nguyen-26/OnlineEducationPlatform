param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('core-lms-service', 'assessment-service', 'worker-service', 'api-gateway')]
    [string]$Service
)

$backend = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\backend')).Path
$settingsPath = Join-Path $backend '.env'
if (-not (Test-Path -LiteralPath $settingsPath)) {
    throw "Create backend/.env with DB_URL, DB_USER, DB_PASSWORD and DB_REPLICA_URL before starting $Service."
}

$settings = @{}
foreach ($line in [System.IO.File]::ReadAllLines($settingsPath)) {
    if ($line -match '^\s*(?:#|$)') { continue }
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $settings[$Matches[1]] = $Matches[2].Trim()
    }
}

foreach ($name in @('DB_URL', 'DB_USER', 'DB_PASSWORD', 'DB_REPLICA_URL', 'DB_REPLICA_USER', 'DB_REPLICA_PASSWORD')) {
    if ($Service -eq 'api-gateway') { break }
    $value = $settings[$name]
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Contains('<')) {
        throw "Set $name to a real local value in backend/.env."
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

if ($Service -eq 'core-lms-service') {
    $aiSettingsPath = Join-Path $PSScriptRoot '..\.env.local'
    if (-not (Test-Path -LiteralPath $aiSettingsPath)) {
        throw 'Create ai-tutor/.env.local from ai-tutor/.env.example first.'
    }
    $secretLine = [System.IO.File]::ReadAllLines($aiSettingsPath) |
        Where-Object { $_ -match '^AI_TUTOR_SERVICE_JWT_SECRET=' } |
        Select-Object -First 1
    if (-not $secretLine -or $secretLine.Substring('AI_TUTOR_SERVICE_JWT_SECRET='.Length).Length -lt 32) {
        throw 'AI_TUTOR_SERVICE_JWT_SECRET in ai-tutor/.env.local must have at least 32 characters.'
    }
    $env:AI_TUTOR_SERVICE_JWT_SECRET = $secretLine.Substring('AI_TUTOR_SERVICE_JWT_SECRET='.Length)
    $storageLine = [System.IO.File]::ReadAllLines($aiSettingsPath) |
        Where-Object { $_ -match '^AI_TUTOR_STORAGE_ORIGIN=' } |
        Select-Object -First 1
    if (-not $storageLine -or [string]::IsNullOrWhiteSpace($storageLine.Substring('AI_TUTOR_STORAGE_ORIGIN='.Length))) {
        throw 'Set AI_TUTOR_STORAGE_ORIGIN in ai-tutor/.env.local to the local MinIO origin.'
    }
    # M2's signed download grants must use the same origin that M7 accepts.
    $env:STORAGE_ENDPOINT = $storageLine.Substring('AI_TUTOR_STORAGE_ORIGIN='.Length)
    $env:AI_TUTOR_ENABLED = 'true'
    $env:AI_TUTOR_BASE_URL = 'http://127.0.0.1:8000'
    $env:SPRING_PROFILES_ACTIVE = 'dev,demo'
}

$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
$env:OTEL_TRACES_EXPORTER = 'none'
$env:OTEL_SDK_DISABLED = 'true'
$mavenCache = 'E:\Caches\OnlineEducationPlatform\maven'
$mavenArgs = @()
if (Test-Path -LiteralPath $mavenCache) {
    $mavenArgs += "-Dmaven.repo.local=$mavenCache"
}

Push-Location $backend
try {
    & mvn @mavenArgs -pl $Service spring-boot:run -q
    if ($LASTEXITCODE -ne 0) { throw "$Service failed with Maven exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
