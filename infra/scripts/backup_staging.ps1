<#
.SYNOPSIS
    Durable external backup script for Hosil Staging PostgreSQL database.

.DESCRIPTION
    Executes a custom-format pg_dump inside the hosil-staging-postgres container,
    transfers the resulting dump file directly to host-side durable storage (infra/backups
    or user-specified destination), verifies file existence and non-zero size, computes SHA256,
    and prunes backups older than the retention threshold.

.PARAMETER BackupDir
    Host directory to store the backup dump. Defaults to infra/backups.

.PARAMETER ContainerName
    Name of the Docker container running staging PostgreSQL. Defaults to 'hosil-staging-postgres'.

.PARAMETER DatabaseName
    Name of the database to dump. Defaults to 'hosil_staging'.

.PARAMETER DbUser
    Username for database connection. Defaults to 'hosil_staging_user'.

.PARAMETER RetentionDays
    Number of days to retain backups before automatic cleanup. Defaults to 14 days.
#>
[CmdletBinding()]
param(
    [string]$BackupDir = "",
    [string]$ContainerName = "hosil-staging-postgres",
    [string]$DatabaseName = "hosil_staging",
    [string]$DbUser = "hosil_staging_user",
    [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($BackupDir)) {
    $scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
    $BackupDir = Join-Path $scriptDir "..\backups"
}

Write-Host "=== Hosil Staging Database External Backup ===" -ForegroundColor Cyan
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Target Database: $DatabaseName"
Write-Host "Container: $ContainerName"

# 1. Validate Docker container status
$containerStatus = docker inspect --format '{{.State.Status}}' $ContainerName 2>$null
if ($containerStatus -ne "running") {
    Write-Error "Error: Container '$ContainerName' is not running (status: '$containerStatus'). Backup aborted."
    exit 1
}

# 2. Resolve and ensure host backup directory exists
$resolvedBackupDir = [System.IO.Path]::GetFullPath($BackupDir)
if (-not (Test-Path -Path $resolvedBackupDir)) {
    New-Item -ItemType Directory -Force -Path $resolvedBackupDir | Out-Null
}
Write-Host "Host Backup Directory: $resolvedBackupDir"

# 3. Formulate backup paths
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dumpFileName = "${DatabaseName}_${timestamp}.dump"
$containerTmpDump = "/tmp/$dumpFileName"
$hostDumpPath = Join-Path $resolvedBackupDir $dumpFileName

Write-Host "Dumping database '$DatabaseName' to container temporary path..."
try {
    # Run pg_dump inside container using custom format (-F c) with blobs (-b)
    docker exec $ContainerName pg_dump -U $DbUser -d $DatabaseName -F c -b -f $containerTmpDump
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dump command failed with exit code $LASTEXITCODE."
    }

    Write-Host "Copying dump from container to host durable storage..."
    docker cp "${ContainerName}:${containerTmpDump}" $hostDumpPath
    if ($LASTEXITCODE -ne 0) {
        throw "docker cp failed with exit code $LASTEXITCODE."
    }
}
finally {
    # Always clean container temporary dump
    docker exec $ContainerName rm -f $containerTmpDump 2>$null | Out-Null
}

# 4. Host-side verification
if (-not (Test-Path -Path $hostDumpPath)) {
    Write-Error "Verification failed: Dump file does not exist at '$hostDumpPath'."
    exit 2
}

$fileInfo = Get-Item -Path $hostDumpPath
$fileSizeBytes = $fileInfo.Length

if ($fileSizeBytes -eq 0) {
    Remove-Item -Path $hostDumpPath -Force
    Write-Error "Verification failed: Dump file is empty (0 bytes). Backup failed."
    exit 3
}

$sha256 = (Get-FileHash -Path $hostDumpPath -Algorithm SHA256).Hash

Write-Host "Backup Successful!" -ForegroundColor Green
Write-Host "File: $hostDumpPath"
Write-Host "Size: $([math]::Round($fileSizeBytes / 1KB, 2)) KB ($fileSizeBytes bytes)"
Write-Host "SHA256: $sha256"

# 5. Apply Retention Policy
if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    $oldBackups = Get-ChildItem -Path $resolvedBackupDir -Filter "${DatabaseName}_*.dump" |
        Where-Object { $_.LastWriteTime -lt $cutoff }

    foreach ($old in $oldBackups) {
        Write-Host "Pruning expired backup: $($old.Name) (Created $($old.LastWriteTime))" -ForegroundColor Yellow
        Remove-Item -Path $old.FullName -Force
    }
}

Write-Host "=== Backup Process Complete ===" -ForegroundColor Cyan
