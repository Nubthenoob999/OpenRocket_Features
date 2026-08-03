[CmdletBinding()]
param(
    [string] $ResearchRoot = (Join-Path $PSScriptRoot '..\build\research'),
    [switch] $SkipRasaeroDownload
)

$ErrorActionPreference = 'Stop'
$rasaeroUrl = 'https://www.rasaero.com/dloads/RASAero_II_Setup_Version_1.0.2.0.zip'
$supersonicUrl = 'https://github.com/AidanSYu/openrocketsupersonic.git'
$supersonicRevision = '73a3f755c9e4efeea1e188f55873e48715046eda'
$supersonicBranch = 'supersonic-aero-dev'

$research = [IO.Path]::GetFullPath($ResearchRoot)
New-Item -ItemType Directory -Force -Path $research | Out-Null

if (-not $SkipRasaeroDownload) {
    $archive = Join-Path $research 'RASAero_II_Setup_Version_1.0.2.0.zip'
    $expanded = Join-Path $research 'rasaero-ii-1.0.2.0'
    if (-not (Test-Path -LiteralPath $archive)) {
        Invoke-WebRequest -Uri $rasaeroUrl -OutFile $archive
    }
    if (-not (Test-Path -LiteralPath $expanded)) {
        Expand-Archive -LiteralPath $archive -DestinationPath $expanded
    }
    $examples = Get-ChildItem -LiteralPath $expanded -Recurse -Directory |
        Where-Object { $_.Name -eq 'Examples' } | Select-Object -First 1
    if ($null -eq $examples) {
        throw "RASAero Examples directory was not found under $expanded"
    }
    $cdx1Count = @(Get-ChildItem -LiteralPath $examples.FullName -File -Filter '*.CDX1').Count
    if ($cdx1Count -ne 24) {
        throw "Expected 24 RASAero Examples CDX1 files, found $cdx1Count"
    }
    Write-Output "RASAero examples root: $($examples.FullName)"
}

$supersonic = Join-Path $research 'openrocketsupersonic-supersonic-aero-dev'
if (-not (Test-Path -LiteralPath $supersonic)) {
    git clone --depth 1 --branch $supersonicBranch $supersonicUrl $supersonic
}
$actualRevision = (git -C $supersonic rev-parse HEAD).Trim()
if ($actualRevision -ne $supersonicRevision) {
    throw "Unexpected OpenRocket Supersonic revision: $actualRevision (expected $supersonicRevision)"
}

$required = @(
    'simvreal/Docs/Mesos/MESOS 293K Flight.CDX1',
    'paper/data/ork/sounding_rockets/bbv.ork',
    'paper/data/ork/sounding_rockets/nike_deacon_flight1.ork',
    'paper/data/ork/sounding_rockets/nike_deacon_flight2.ork'
)
foreach ($relative in $required) {
    if (-not (Test-Path -LiteralPath (Join-Path $supersonic $relative))) {
        throw "Pinned source model missing: $relative"
    }
}
Write-Output "OpenRocket Supersonic root: $supersonic"
Write-Output "For Java tests set -Dopenrocket.externalFlightModels.openRocketSupersonicRoot=$supersonic"
