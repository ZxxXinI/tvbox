param(
    [Parameter(Mandatory = $true)]
    [string]$VersionName,

    [Parameter(Mandatory = $true)]
    [int]$VersionCode,

    [string]$ApkPath = "",
    [string]$UpdateJsonPath = "",
    [string]$ReleaseNotesPath = "",
    [string]$GithubRepo = "ZxxXinI/tvbox",
    [string]$S3Prefix = "tvbox/releases",
    [string]$S3Endpoint = "",
    [string]$S3Region = "",
    [string]$S3Bucket = "",
    [string]$S3PublicBaseUrl = "",
    [string[]]$Changelog = @(),
    [switch]$SkipS3Upload,
    [switch]$SkipGithubUpload,
    [switch]$NoAcl,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Read-LocalProperties {
    $root = Get-RepoRoot
    $path = Join-Path $root "local.properties"
    $values = @{}
    if (-not (Test-Path $path)) {
        return $values
    }
    foreach ($rawLine in Get-Content -LiteralPath $path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            continue
        }
        $separator = $line.IndexOf("=")
        if ($separator -le 0) {
            continue
        }
        $key = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        $values[$key] = $value
    }
    return $values
}

function Get-ConfigValue {
    param(
        [hashtable]$LocalProperties,
        [string]$Name,
        [string]$DefaultValue = ""
    )
    $envValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) {
        return $envValue
    }
    if ($LocalProperties.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($LocalProperties[$Name])) {
        return $LocalProperties[$Name]
    }
    return $DefaultValue
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $Name"
    }
}

function Read-ChangelogFromReleaseNotes {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return @()
    }

    $items = New-Object System.Collections.Generic.List[string]
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("- ")) {
            $items.Add($trimmed.Substring(2).Trim())
        }
    }
    return $items.ToArray()
}

function ConvertTo-HexString {
    param([byte[]]$Bytes)
    return (($Bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Get-Sha256String {
    param([string]$Value)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return ConvertTo-HexString $sha256.ComputeHash($bytes)
    } finally {
        $sha256.Dispose()
    }
}

function Get-HmacSha256Bytes {
    param(
        [byte[]]$Key,
        [string]$Value
    )
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Value)
        return $hmac.ComputeHash($bytes)
    } finally {
        $hmac.Dispose()
    }
}

function ConvertTo-S3PathSegment {
    param([string]$Value)
    return [System.Uri]::EscapeDataString($Value).Replace("%2F", "/")
}

function Invoke-S3PutObject {
    param(
        [string]$Endpoint,
        [string]$Region,
        [string]$Bucket,
        [string]$Key,
        [string]$FilePath,
        [string]$ContentType,
        [string]$AccessKey,
        [string]$SecretKey,
        [bool]$UsePublicReadAcl
    )

    $baseEndpoint = $Endpoint.TrimEnd("/")
    $encodedBucket = ConvertTo-S3PathSegment $Bucket
    $encodedKey = ($Key -split "/" | ForEach-Object { ConvertTo-S3PathSegment $_ }) -join "/"
    $uri = [System.Uri]"$baseEndpoint/$encodedBucket/$encodedKey"
    $canonicalUri = "/$encodedBucket/$encodedKey"
    $payloadHash = (Get-FileHash -LiteralPath $FilePath -Algorithm SHA256).Hash.ToLower()
    $now = (Get-Date).ToUniversalTime()
    $amzDate = $now.ToString("yyyyMMddTHHmmssZ", [System.Globalization.CultureInfo]::InvariantCulture)
    $dateStamp = $now.ToString("yyyyMMdd", [System.Globalization.CultureInfo]::InvariantCulture)

    $headers = @{
        "content-type" = $ContentType
        "host" = $uri.Authority.ToLowerInvariant()
        "x-amz-content-sha256" = $payloadHash
        "x-amz-date" = $amzDate
    }
    if ($UsePublicReadAcl) {
        $headers["x-amz-acl"] = "public-read"
    }

    $sortedHeaderNames = $headers.Keys | Sort-Object
    $canonicalHeaders = ""
    foreach ($headerName in $sortedHeaderNames) {
        $canonicalHeaders += "${headerName}:$($headers[$headerName])`n"
    }
    $signedHeaders = ($sortedHeaderNames -join ";")
    $canonicalRequest = "PUT`n$canonicalUri`n`n$canonicalHeaders`n$signedHeaders`n$payloadHash"
    $canonicalRequestHash = Get-Sha256String $canonicalRequest

    $algorithm = "AWS4-HMAC-SHA256"
    $credentialScope = "$dateStamp/$Region/s3/aws4_request"
    $stringToSign = "$algorithm`n$amzDate`n$credentialScope`n$canonicalRequestHash"

    $dateKey = Get-HmacSha256Bytes ([System.Text.Encoding]::UTF8.GetBytes("AWS4$SecretKey")) $dateStamp
    $regionKey = Get-HmacSha256Bytes $dateKey $Region
    $serviceKey = Get-HmacSha256Bytes $regionKey "s3"
    $signingKey = Get-HmacSha256Bytes $serviceKey "aws4_request"
    $signature = ConvertTo-HexString (Get-HmacSha256Bytes $signingKey $stringToSign)
    $authorization = "$algorithm Credential=$AccessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

    $client = [System.Net.Http.HttpClient]::new()
    $stream = [System.IO.File]::OpenRead($FilePath)
    try {
        $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Put, $uri)
        $content = [System.Net.Http.StreamContent]::new($stream)
        $content.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $request.Content = $content
        $request.Headers.Host = $headers["host"]
        $request.Headers.TryAddWithoutValidation("x-amz-content-sha256", $headers["x-amz-content-sha256"]) | Out-Null
        $request.Headers.TryAddWithoutValidation("x-amz-date", $headers["x-amz-date"]) | Out-Null
        if ($UsePublicReadAcl) {
            $request.Headers.TryAddWithoutValidation("x-amz-acl", $headers["x-amz-acl"]) | Out-Null
        }
        $request.Headers.TryAddWithoutValidation("Authorization", $authorization) | Out-Null

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "S3 upload failed: HTTP $([int]$response.StatusCode) $($response.ReasonPhrase). $responseBody"
        }
    } finally {
        if ($request) { $request.Dispose() }
        if ($content) { $content.Dispose() }
        $stream.Dispose()
        $client.Dispose()
    }
}

$rootDir = Get-RepoRoot
$localProperties = Read-LocalProperties

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $rootDir "app\build\outputs\apk\release\TVBox-v$VersionName.apk"
}
if ([string]::IsNullOrWhiteSpace($UpdateJsonPath)) {
    $UpdateJsonPath = Join-Path $rootDir "app\build\outputs\apk\release\update.json"
}
if ([string]::IsNullOrWhiteSpace($ReleaseNotesPath)) {
    $ReleaseNotesPath = Join-Path $rootDir "app\build\outputs\apk\release\release-notes-v$VersionName.md"
}

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
}

if ([string]::IsNullOrWhiteSpace($S3Endpoint)) {
    $endpoint = Get-ConfigValue $localProperties "TVBOX_S3_ENDPOINT" "https://s3.cstcloud.cn"
} else {
    $endpoint = $S3Endpoint
}

if ([string]::IsNullOrWhiteSpace($S3Bucket)) {
    $bucket = Get-ConfigValue $localProperties "TVBOX_S3_BUCKET"
} else {
    $bucket = $S3Bucket
}
if ([string]::IsNullOrWhiteSpace($bucket)) {
    throw "Missing TVBOX_S3_BUCKET. Set it in local.properties or environment variables."
}

if ([string]::IsNullOrWhiteSpace($S3Region)) {
    $region = Get-ConfigValue $localProperties "TVBOX_S3_REGION" "us-east-1"
} else {
    $region = $S3Region
}

if ([string]::IsNullOrWhiteSpace($S3PublicBaseUrl)) {
    $publicBaseUrl = Get-ConfigValue $localProperties "TVBOX_S3_PUBLIC_BASE_URL" "$($endpoint.TrimEnd('/'))/$bucket"
} else {
    $publicBaseUrl = $S3PublicBaseUrl
}

$apkFile = Get-Item -LiteralPath $ApkPath
$apkName = "TVBox-v$VersionName.apk"
$normalizedPrefix = $S3Prefix.Trim("/").Replace("\", "/")
$s3Key = "$normalizedPrefix/v$VersionName/$apkName"
$apkUrl = "$($publicBaseUrl.TrimEnd('/'))/$s3Key"
$apkSha256 = (Get-FileHash -LiteralPath $apkFile.FullName -Algorithm SHA256).Hash.ToLower()
$apkSize = $apkFile.Length

$manifestChangelog = @()
if ($Changelog.Count -gt 0) {
    $manifestChangelog = $Changelog
} else {
    $fromNotes = Read-ChangelogFromReleaseNotes $ReleaseNotesPath
    if ($fromNotes.Count -gt 0) {
        $manifestChangelog = $fromNotes
    } else {
        $manifestChangelog = @("See GitHub Release notes.")
    }
}

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apkUrl = $apkUrl
    apkSha256 = $apkSha256
    apkSize = $apkSize
    force = $false
    changelog = $manifestChangelog
}

$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $UpdateJsonPath -Encoding UTF8

Write-Host "APK: $($apkFile.FullName)"
Write-Host "Size: $apkSize"
Write-Host "SHA-256: $apkSha256"
Write-Host "S3 URL: $apkUrl"
Write-Host "update.json: $UpdateJsonPath"

if ($DryRun) {
    Write-Host "Dry run: skipped S3 and GitHub uploads."
    exit 0
}

if (-not $SkipS3Upload) {
    $accessKey = Get-ConfigValue $localProperties "TVBOX_S3_ACCESS_KEY_ID" (Get-ConfigValue $localProperties "AWS_ACCESS_KEY_ID")
    $secretKey = Get-ConfigValue $localProperties "TVBOX_S3_SECRET_ACCESS_KEY" (Get-ConfigValue $localProperties "AWS_SECRET_ACCESS_KEY")
    if ([string]::IsNullOrWhiteSpace($accessKey) -or [string]::IsNullOrWhiteSpace($secretKey)) {
        throw "Missing S3 credentials. Set TVBOX_S3_ACCESS_KEY_ID and TVBOX_S3_SECRET_ACCESS_KEY in local.properties or environment variables."
    }

    Invoke-S3PutObject `
        -Endpoint $endpoint `
        -Region $region `
        -Bucket $bucket `
        -Key $s3Key `
        -FilePath $apkFile.FullName `
        -ContentType "application/vnd.android.package-archive" `
        -AccessKey $accessKey `
        -SecretKey $secretKey `
        -UsePublicReadAcl (-not $NoAcl)

    Write-Host "S3 upload completed."
}

if (-not $SkipGithubUpload) {
    Require-Command "gh"
    $tag = "v$VersionName"
    $releaseExists = $false

    $releaseViewOutput = & gh release view $tag --repo $GithubRepo 2>$null
    if ($LASTEXITCODE -eq 0) {
        $releaseExists = $true
    }

    if ($releaseExists) {
        & gh release upload $tag $apkFile.FullName $UpdateJsonPath --repo $GithubRepo --clobber
    } else {
        $createArgs = @(
            "release", "create", $tag,
            $apkFile.FullName,
            $UpdateJsonPath,
            "--repo", $GithubRepo,
            "--title", "TVBox v$VersionName"
        )
        if (Test-Path $ReleaseNotesPath) {
            $createArgs += @("--notes-file", $ReleaseNotesPath)
        } else {
            $createArgs += @("--notes", "TVBox v$VersionName")
        }
        & gh @createArgs
    }
}
