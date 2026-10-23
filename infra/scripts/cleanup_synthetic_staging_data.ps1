<#
.SYNOPSIS
    Synthetic data purge utility for Hosil Staging database.

.DESCRIPTION
    Safely executes cleanup_synthetic_staging_data.sql against hosil_staging,
    resetting transactional test records between pilot cohorts while preserving
    catalog taxonomies, roles, and schema history.

.PARAMETER ConfirmExecution
    Mandatory safety switch to prevent accidental purges.
#>
[CmdletBinding()]
param(
    [switch]$ConfirmExecution,
    [string]$ContainerName = "hosil-staging-postgres",
    [string]$DatabaseName = "hosil_staging",
    [string]$DbUser = "hosil_staging_user"
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmExecution) {
    Write-Warning "Safety Check: To execute synthetic staging data cleanup, you must pass -ConfirmExecution."
    Write-Warning "Example: .\cleanup_synthetic_staging_data.ps1 -ConfirmExecution"
    exit 1
}

$scriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
$sqlFile = Join-Path $scriptDir "cleanup_synthetic_staging_data.sql"

if (-not (Test-Path $sqlFile)) {
    Write-Error "SQL script not found at $sqlFile"
    exit 2
}

Write-Host "Purging synthetic test data from '$DatabaseName' in container '$ContainerName'..." -ForegroundColor Yellow

Get-Content $sqlFile -Raw | docker exec -i $ContainerName psql -U $DbUser -d $DatabaseName
if ($LASTEXITCODE -ne 0) {
    Write-Error "Data cleanup failed."
    exit 3
}

Write-Host "Synthetic staging data purged successfully. Master catalogs and schema history preserved." -ForegroundColor Green
