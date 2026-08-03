[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceCsv,

    [switch] $ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = (Resolve-Path -LiteralPath $SourceCsv).Path
if (-not [System.IO.File]::Exists($sourcePath)) {
    throw "CFD source is not a regular file: $sourcePath"
}

$lines = @([System.IO.File]::ReadAllLines($sourcePath, [System.Text.Encoding]::UTF8) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($lines.Count -lt 3) {
    throw 'CFD surface must contain a header and at least two numeric rows.'
}

$header = $lines[0]
$delimiterCounts = @{
    ','  = @($header.ToCharArray() | Where-Object { $_ -eq ',' }).Count
    ';'  = @($header.ToCharArray() | Where-Object { $_ -eq ';' }).Count
    "`t" = @($header.ToCharArray() | Where-Object { $_ -eq "`t" }).Count
}
$delimiterText = $delimiterCounts.GetEnumerator() |
    Sort-Object Value -Descending |
    Select-Object -First 1 -ExpandProperty Key
if ($delimiterCounts[$delimiterText] -lt 2) {
    throw 'CFD header must contain at least three delimited columns.'
}
$delimiter = [char]$delimiterText
$rows = @($lines | ConvertFrom-Csv -Delimiter $delimiter)
if ($rows.Count -lt 2) {
    throw 'CFD surface has fewer than two parsed data rows.'
}

function Normalize-Header([string] $name) {
    return ($name.ToLowerInvariant() -replace '[^a-z0-9]', '')
}

function Test-Finite([double] $value) {
    return -not [double]::IsNaN($value) -and -not [double]::IsInfinity($value)
}

$properties = @($rows[0].PSObject.Properties.Name)
$normalized = @{}
foreach ($property in $properties) {
    $normalized[(Normalize-Header $property)] = $property
}

function Find-Column([string[]] $aliases, [switch] $RejectCd) {
    foreach ($alias in $aliases) {
        $key = Normalize-Header $alias
        if ($normalized.ContainsKey($key)) {
            $candidate = $normalized[$key]
            if (-not $RejectCd -or (Normalize-Header $candidate) -notmatch 'cd') {
                return $candidate
            }
        }
    }
    foreach ($entry in $normalized.GetEnumerator()) {
        if ($RejectCd -and $entry.Key -match 'cd') {
            continue
        }
        foreach ($alias in $aliases) {
            if ($entry.Key.Contains((Normalize-Header $alias))) {
                return $entry.Value
            }
        }
    }
    return $null
}

$machColumn = Find-Column @('mach', 'machnumber')
$deploymentColumn = Find-Column @('deployment', 'deploymentpercentage', 'deploymentfraction', 'deploy', 'extension', 'airbrakeext')
$dragColumn = Find-Column @('dragn', 'dragforce', 'deltadrag', 'drag', 'force') -RejectCd
if (-not $machColumn -or -not $deploymentColumn -or -not $dragColumn) {
    throw "Expected Mach, deployment, and absolute drag-force columns; coefficient-only data is invalid. Headers: $($properties -join ', ')"
}

$culture = [System.Globalization.CultureInfo]::InvariantCulture
$numberStyle = [System.Globalization.NumberStyles]::Float
$samples = [System.Collections.Generic.List[object]]::new()
foreach ($row in $rows) {
    $mach = 0.0
    $deployment = 0.0
    $dragN = 0.0
    if (-not [double]::TryParse([string]$row.$machColumn, $numberStyle, $culture, [ref]$mach) -or
        -not [double]::TryParse([string]$row.$deploymentColumn, $numberStyle, $culture, [ref]$deployment) -or
        -not [double]::TryParse([string]$row.$dragColumn, $numberStyle, $culture, [ref]$dragN)) {
        throw "Non-numeric CFD row: $($row | ConvertTo-Json -Compress)"
    }
    if (-not (Test-Finite $mach) -or -not (Test-Finite $deployment) -or
        -not (Test-Finite $dragN) -or $mach -lt 0 -or $dragN -lt 0) {
        throw "CFD values must be finite with Mach >= 0 and drag force >= 0 N: $($row | ConvertTo-Json -Compress)"
    }
    $samples.Add([pscustomobject]@{ Mach = $mach; Deployment = $deployment; DragN = $dragN })
}

$maximumDeployment = ($samples | Measure-Object Deployment -Maximum).Maximum
$minimumDeployment = ($samples | Measure-Object Deployment -Minimum).Minimum
if ($minimumDeployment -lt 0) {
    throw 'Deployment values must not be negative.'
}
if ($maximumDeployment -le 1.000001) {
    $deploymentScale = 1.0
} elseif ($maximumDeployment -le 100.000001) {
    $deploymentScale = 0.01
} else {
    throw 'Deployment must be a fraction in [0,1] or a percentage in [0,100].'
}
foreach ($sample in $samples) {
    $sample.Deployment *= $deploymentScale
    if ($sample.Deployment -lt 0 -or $sample.Deployment -gt 1.000001) {
        throw 'Mixed or out-of-range deployment units are not accepted.'
    }
}

$machAnchors = @($samples.Mach | Sort-Object -Unique)
$deploymentAnchors = @($samples.Deployment | Sort-Object -Unique)
if ($machAnchors.Count -lt 2 -or $deploymentAnchors.Count -lt 2) {
    throw "Surface requires at least two Mach and two deployment anchors; got $($machAnchors.Count) x $($deploymentAnchors.Count)."
}
if ($deploymentAnchors[0] -gt 0.05 -or $deploymentAnchors[-1] -lt 0.95) {
    throw 'Deployment axis must include both a retracted (~0) and deployed (~1) anchor.'
}

foreach ($group in ($samples | Group-Object Mach)) {
    $ordered = @($group.Group | Sort-Object Deployment)
    for ($index = 1; $index -lt $ordered.Count; $index++) {
        if ($ordered[$index].DragN + 1.0e-9 -lt $ordered[$index - 1].DragN) {
            throw "Drag force decreases with deployment at Mach $($group.Name); verify column units and source data."
        }
    }
}

$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $sourcePath).Hash
Write-Output "Validated Pelicantor CFD surface: rows=$($samples.Count), Mach=$($machAnchors.Count), deployment=$($deploymentAnchors.Count), SHA256=$sourceHash"

if ($ValidateOnly) {
    return
}

$relativeDestinations = @(
    'core\src\test\java\info\openrocket\core\tuning\DOL\Drag Curve Pelicantor - Sheet1.csv',
    'core\src\test\java\info\openrocket\core\tuning\Pelencator_launch_1\Drag Curve Pelicantor - Sheet1.csv',
    'core\src\test\java\info\openrocket\core\tuning\Pelencator_Launch_Hunts\Drag Curve Pelicantor - Sheet1.csv'
)
foreach ($relativeDestination in $relativeDestinations) {
    $destination = Join-Path $repositoryRoot $relativeDestination
    $destinationDirectory = Split-Path -Parent $destination
    if (-not [System.IO.Directory]::Exists($destinationDirectory)) {
        throw "Expected corpus directory is missing: $destinationDirectory"
    }
    Copy-Item -LiteralPath $sourcePath -Destination $destination -Force
    $destinationHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash
    if ($destinationHash -ne $sourceHash) {
        throw "Restored surface hash mismatch: $destination"
    }
    Write-Output "Restored unchanged bytes: $destination"
}
