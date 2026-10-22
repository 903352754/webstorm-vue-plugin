<#
.SYNOPSIS
  Builds the plugin without Gradle, directly against a locally installed WebStorm.

.DESCRIPTION
  Compiles the sources with the JBR compiler of the installed IDE and packages the result into
  build/distributions/vue3-setup-creator-<version>.zip, which can be installed in WebStorm via
  "Settings | Plugins | gear icon | Install Plugin from Disk...".

  No network access and no Gradle download are required.

.PARAMETER IdePath
  Path to the WebStorm installation. autodetected when omitted.

.EXAMPLE
  ./build-local.ps1
  ./build-local.ps1 -IdePath "D:\JetBrains\WebStorm 2026.2.1"
#>
[CmdletBinding()]
param(
  [string]$IdePath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$pluginId = 'vue3-setup-creator'
$pluginVersion = '1.0.0'

function Find-Ide {
  param([string]$Explicit)

  if ($Explicit) {
    if (-not (Test-Path $Explicit)) { throw "IDE path does not exist: $Explicit" }
    return (Resolve-Path $Explicit).Path
  }

  $candidates = @()
  foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
    if (-not $base) { continue }
    $jetbrains = Join-Path $base 'JetBrains'
    if (Test-Path $jetbrains) {
      $candidates += Get-ChildItem $jetbrains -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'WebStorm*' -and (Test-Path (Join-Path $_.FullName 'jbr\bin')) } |
        Select-Object -ExpandProperty FullName
    }
  }
  if ($candidates.Count -eq 0) {
    throw 'WebStorm was not found. Pass -IdePath <path to WebStorm>.'
  }
  # Prefer the newest installation.
  return ($candidates | Sort-Object -Descending | Select-Object -First 1)
}

$ide = Find-Ide -Explicit $IdePath
Write-Host "IDE:           $ide"

$javac = Join-Path $ide 'jbr\bin\javac.exe'
if (-not (Test-Path $javac)) { throw "javac not found in $ide\jbr\bin" }

# --- classpath ---------------------------------------------------------------------------------
$classPathEntries = @(
  (Join-Path $ide 'lib'),
  (Join-Path $ide 'plugins\javascript-plugin\lib\modules'),
  (Join-Path $ide 'plugins\vuejs-plugin\lib\modules')
) | Where-Object { Test-Path $_ } | ForEach-Object { "$_\*" }
if ($classPathEntries.Count -eq 0) { throw 'No platform jars found in the IDE installation.' }
$classPath = $classPathEntries -join ';'

# --- compile -----------------------------------------------------------------------------------
$classesDir = Join-Path $projectRoot "build\classes\$pluginId"
$resourcesDir = Join-Path $projectRoot "build\resources\$pluginId"
$distDir = Join-Path $projectRoot 'build\distributions'
$jarDir = Join-Path $projectRoot "build\jar\$pluginId"

foreach ($dir in @($classesDir, $resourcesDir, $distDir, $jarDir)) {
  if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$sources = Get-ChildItem (Join-Path $projectRoot 'src\main\java') -Recurse -Filter *.java |
  Select-Object -ExpandProperty FullName
if (-not $sources) { throw 'No sources found.' }

Write-Host 'Compiling...'
& $javac -nowarn -encoding UTF-8 -d $classesDir -cp $classPath $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

Write-Host 'Copying resources...'
Copy-Item (Join-Path $projectRoot 'src\main\resources\*') $classesDir -Recurse -Force

# --- package -----------------------------------------------------------------------------------
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# .NET Framework writes backslashes into zip entries on Windows, which breaks jars and plugin
# distributions. The archive is therefore created manually with POSIX style entry names.
function New-ZipFromDirectory {
  param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [Parameter(Mandatory = $true)][string]$ZipPath
  )
  if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
  $base = (Resolve-Path $SourceDir).Path.TrimEnd('\')
  $archive = [System.IO.Compression.ZipFile]::Open($ZipPath, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    foreach ($file in Get-ChildItem $SourceDir -Recurse -File) {
      $relative = $file.FullName.Substring($base.Length + 1).Replace('\', '/')
      [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $archive, $file.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
    }
  }
  finally {
    $archive.Dispose()
  }
}

$jarPath = Join-Path $jarDir "$pluginId.jar"
New-ZipFromDirectory -SourceDir $classesDir -ZipPath $jarPath

$pluginDir = Join-Path $projectRoot "build\plugin\$pluginId"
if (Test-Path $pluginDir) { Remove-Item $pluginDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $pluginDir 'lib') | Out-Null
Copy-Item $jarPath (Join-Path $pluginDir "lib\$pluginId.jar")

$zipPath = Join-Path $distDir "$pluginId-$pluginVersion.zip"
New-ZipFromDirectory -SourceDir $pluginDir -ZipPath $zipPath
# The distribution contains the plugin directory as root folder, the same layout the Gradle build
# produces.
$stagingDir = Join-Path $projectRoot "build\zip-staging"
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null
Copy-Item $pluginDir (Join-Path $stagingDir $pluginId) -Recurse -Force
New-ZipFromDirectory -SourceDir $stagingDir -ZipPath $zipPath
Remove-Item $stagingDir -Recurse -Force

Write-Host ''
Write-Host "Built: $zipPath" -ForegroundColor Green
Write-Host 'Install it in WebStorm: Settings | Plugins | gear icon | Install Plugin from Disk...'
