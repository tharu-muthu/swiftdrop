param([switch]$Compose)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$target = if ($Compose) { Join-Path $projectRoot '.env' } else { Join-Path $projectRoot 'backend/.env' }
$content = if (Test-Path -LiteralPath $target) { [IO.File]::ReadAllText($target) } else { '' }
$defaults = [ordered]@{
    AUTH_JWT_SECRET = ''
    AUTH_JWT_ISSUER = 'https://swiftdrop.local'
    AUTH_JWT_AUDIENCE = 'swiftdrop-api'
    AUTH_ACCESS_TTL = '10m'
    AUTH_REFRESH_TTL = '7d'
    AUTH_LOCALHOST_DEVELOPMENT = 'true'
    AUTH_ALLOWED_ORIGINS = 'http://localhost:5173,http://127.0.0.1:5173,http://localhost:4173,http://127.0.0.1:4173'
}
foreach ($name in $defaults.Keys) {
    $pattern = '(?m)^' + [regex]::Escape($name) + '=(.*)$'
    $match = [regex]::Match($content, $pattern)
    if ($match.Success -and $match.Groups[1].Value.Trim() -ne '' -and -not $match.Groups[1].Value.StartsWith('YOUR_')) { continue }
    $value = $defaults[$name]
    if ($name -eq 'AUTH_JWT_SECRET') {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes); $value = [Convert]::ToBase64String($bytes) } finally { $random.Dispose() }
    }
    if ($match.Success) { $content = [regex]::Replace($content, $pattern, "$name=$value") }
    else { $content = $content.TrimEnd("`r", "`n") + "`r`n$name=$value`r`n" }
}
[IO.File]::WriteAllText($target, $content, (New-Object Text.UTF8Encoding($false)))
Write-Output 'Private authentication settings are ready. Existing non-placeholder values and database configuration were preserved.'
