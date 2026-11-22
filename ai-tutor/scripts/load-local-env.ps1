param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env.local')
)

$resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
foreach ($line in [System.IO.File]::ReadAllLines($resolved.Path)) {
    if ($line -match '^\s*(?:#|$)') { continue }
    if ($line -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        throw "Invalid environment entry in $($resolved.Path)"
    }
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
}

Write-Output 'Loaded local AI Tutor settings into this PowerShell process.'
