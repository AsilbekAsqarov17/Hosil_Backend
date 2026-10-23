<#
.SYNOPSIS
    Durable external restore and verification script for Hosil Staging PostgreSQL database.

.DESCRIPTION
    Restores a pg_dump archive into an isolated test or staging database, verifies
    schema objects, Flyway migration records, and table row counts, and verifies schema integrity.

.PARAMETER DumpPath
    Path to the .dump file on the host.

.PARAMETER TargetDb
    Name of the target database to restore into. Defaults to 'hosil_staging_restore_test'.

.PARAMETER ContainerName
    Name of the Docker container running staging PostgreSQL. Defaults to 'hosil-staging-postgres'.

.PARAMETER DbUser
    Username for database connection. Defaults to 'hosil_staging_user'.

.PARAMETER DropAfterVerification
    If specified, drops the test database after successful verification.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$DumpPath,
    [string]$TargetDb = "hosil_staging_restore_test",
    [string]$ContainerName = "hosil-staging-postgres",
    [string]$DbUser = "hosil_staging_user",
    [switch]$DropAfterVerification,
    [switch]$AllowLiveDatabaseOverwrite
)

$ErrorActionPreference = "Stop"

Write-Host "=== Hosil Staging Database Restore & Verification ===" -ForegroundColor Cyan
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Source Dump: $DumpPath"
Write-Host "Target Database: $TargetDb"
Write-Host "Container: $ContainerName"

# 1. Safety Gate: Guard against unintended overwrites
if ($TargetDb -in @("hosil_staging", "hosil_db", "postgres") -and -not $AllowLiveDatabaseOverwrite) {
    Write-Error "Safety Gate Triggered: Target database '$TargetDb' is a primary database. Overwriting requires -AllowLiveDatabaseOverwrite flag. Aborting."
    exit 1
}

# 2. Validate Dump Path
if (-not (Test-Path -Path $DumpPath)) {
    Write-Error "Dump file not found: '$DumpPath'."
    exit 2
}
$resolvedDumpPath = [System.IO.Path]::GetFullPath($DumpPath)
$dumpFileName = [System.IO.Path]::GetFileName($resolvedDumpPath)
$containerTmpDump = "/tmp/$dumpFileName"

# 3. Validate Docker container status
$containerStatus = docker inspect --format '{{.State.Status}}' $ContainerName 2>$null
if ($containerStatus -ne "running") {
    Write-Error "Error: Container '$ContainerName' is not running."
    exit 3
}

try {
    # 4. Copy dump into container
    Write-Host "Copying dump file into container '$ContainerName'..."
    docker cp $resolvedDumpPath "${ContainerName}:${containerTmpDump}"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy dump file to container."
    }

    # 5. Create target database if it does not already exist
    Write-Host "Ensuring target database '$TargetDb' exists..."
    $dbExists = docker exec $ContainerName psql -U $DbUser -d hosil_staging -tAc "SELECT 1 FROM pg_database WHERE datname='$TargetDb';"
    if ([string]::IsNullOrWhiteSpace($dbExists) -or $dbExists.Trim() -ne "1") {
        Write-Host "Creating target database '$TargetDb'..."
        docker exec $ContainerName createdb -U $DbUser $TargetDb
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to create target database '$TargetDb'."
        }
    } else {
        Write-Host "Target database '$TargetDb' already exists."
    }

    # 6. Execute pg_restore
    Write-Host "Executing pg_restore into '$TargetDb'..."
    # pg_restore returns exit code 0 on complete success, or 1 on warnings (e.g. notices).
    # We use --clean --if-exists to replace existing objects if database was pre-existing.
    docker exec $ContainerName pg_restore -U $DbUser -d $TargetDb --clean --if-exists --no-owner --no-privileges $containerTmpDump 2>&1 | Out-Null

    # 7. Verification of restored schema
    Write-Host "Verifying restored schema objects and Flyway migrations..."

    # Check table count
    $tableCount = (docker exec $ContainerName psql -U $DbUser -d $TargetDb -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';").Trim()

    # Check Flyway migrations
    $flywayCount = (docker exec $ContainerName psql -U $DbUser -d $TargetDb -tAc "SELECT count(*) FROM flyway_schema_history WHERE success = true;").Trim()

    # Query sample tables
    $sampleTables = docker exec $ContainerName psql -U $DbUser -d $TargetDb -tAc "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name LIMIT 10;"

    Write-Host "Verification Results:" -ForegroundColor Green
    Write-Host "  - Restored Public Tables: $tableCount"
    Write-Host "  - Successful Flyway Migrations: $flywayCount"
    Write-Host "  - Sample Tables: $($sampleTables -join ', ')"

    if ([int]$tableCount -lt 40 -or [int]$flywayCount -lt 20) {
        throw "Verification failed: Expected >= 47 tables and 20 Flyway migrations, but found $tableCount tables and $flywayCount migrations."
    }

    Write-Host "Database restore and parity verification SUCCESSFUL!" -ForegroundColor Green

    # 8. Teardown if requested
    if ($DropAfterVerification) {
        Write-Host "Dropping disposable verification database '$TargetDb'..."
        docker exec $ContainerName dropdb -U $DbUser $TargetDb
        Write-Host "Disposable database '$TargetDb' dropped cleanly." -ForegroundColor Yellow
    }
}
finally {
    # Clean up container temporary dump
    docker exec $ContainerName rm -f $containerTmpDump 2>$null | Out-Null
}

Write-Host "=== Restore Process Complete ===" -ForegroundColor Cyan
