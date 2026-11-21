# UDS Diagnostic GUI launcher (encoding-safe; use this if run.bat breaks after ZIP download)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
$env:UDS_PROJECT_ROOT = $Root
$env:UDS_APP_HOME = $Root

$outDir = Join-Path $Root "output"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$launchLog = Join-Path $outDir "launch.log"

function Write-Log([string]$msg) {
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $msg
    Add-Content -Path $launchLog -Value $line -Encoding UTF8
}

Set-Content -Path $launchLog -Value ("===== UDS launch {0} =====" -f (Get-Date)) -Encoding UTF8
Write-Log "project=$Root"

# Hide build clutter from Explorer (best-effort)
foreach ($name in @("src", "test", "doc", "target", ".mvn", "mvnw", "mvnw.cmd", ".cursorrules", "pom.xml")) {
    $p = Join-Path $Root $name
    if (Test-Path $p) { attrib +H $p 2>$null | Out-Null }
}

$sessionDir = Join-Path $outDir ".session"
if (-not (Test-Path $sessionDir)) { New-Item -ItemType Directory -Path $sessionDir | Out-Null }
attrib +H $sessionDir 2>$null | Out-Null

function Get-JavaMajor([string]$javaExe) {
    # java -version writes to stderr; with $ErrorActionPreference=Stop that becomes a terminating error.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $raw = & $javaExe -version 2>&1 | ForEach-Object { "$_" }
    } finally {
        $ErrorActionPreference = $prev
    }
    $verLine = ($raw | Select-String -Pattern 'version' | Select-Object -First 1)
    if (-not $verLine) { return 0, "" }
    $text = "$verLine"
    if ($text -match 'version\s+"?([0-9]+)') {
        return [int]$Matches[1], $Matches[1]
    }
    return 0, ""
}

function Resolve-Jdk25 {
    $candidates = @()
    if ($env:JAVA_HOME) {
        $j = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $j) { $candidates += $j }
    }
    $roots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($r in $roots) {
        if (Test-Path $r) {
            Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -like "jdk-25*" } |
                ForEach-Object {
                    $j = Join-Path $_.FullName "bin\java.exe"
                    if (Test-Path $j) { $candidates += $j }
                }
        }
    }
    $where = Get-Command java -ErrorAction SilentlyContinue
    if ($where) { $candidates += $where.Source }

    foreach ($javaExe in $candidates | Select-Object -Unique) {
        $major, $ver = Get-JavaMajor $javaExe
        if ($major -ge 25) {
            # Do not use $home — it is a read-only automatic variable in PowerShell.
            $jdkHome = Split-Path (Split-Path $javaExe -Parent) -Parent
            return $jdkHome, $ver
        }
    }
    return $null, $null
}

function Resolve-Maven([string]$projectRoot) {
    $where = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($where) { return $where.Source }

    Get-ChildItem $env:USERPROFILE -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "apache-maven-*" } |
        ForEach-Object {
            $m = Join-Path $_.FullName "bin\mvn.cmd"
            if (Test-Path $m) { return $m }
        }

    if (Test-Path "C:\apache-maven-3.9.9\bin\mvn.cmd") {
        return "C:\apache-maven-3.9.9\bin\mvn.cmd"
    }

    $wrapper = Join-Path $projectRoot "mvnw.cmd"
    if (Test-Path $wrapper) { return $wrapper }

    return $null
}

$javaHome, $javaVer = Resolve-Jdk25
if (-not $javaHome) {
    Write-Host "ERROR: JDK 25+ not found. Install Temurin 25 or set JAVA_HOME."
    Write-Log "ERROR: JDK 25+ not found"
    Read-Host "Press Enter to exit"
    exit 1
}

$mvn = Resolve-Maven $Root
if (-not $mvn) {
    Write-Host "ERROR: Maven not found. Install Maven or keep project mvnw.cmd."
    Write-Log "ERROR: Maven not found"
    Read-Host "Press Enter to exit"
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$env:SystemRoot\System32;$env:SystemRoot\System32\WindowsPowerShell\v1.0;$javaHome\bin;$env:PATH"
if (-not $env:MAVEN_OPTS) {
    $env:MAVEN_OPTS = "--enable-native-access=ALL-UNNAMED"
}

Write-Host "JAVA_HOME=$javaHome"
Write-Host "java=$javaVer"
Write-Host "mvn=$mvn"
Write-Log "JAVA_HOME=$javaHome"
Write-Log "java=$javaVer"
Write-Log "mvn=$mvn"

Write-Host ""
Write-Host "Starting UDS Diagnostic GUI..."
Write-Host "Log: $launchLog"
Write-Host "Output: $(Join-Path $Root 'output')"
Write-Host ""

$pom = Join-Path $Root "pom.xml"
& $mvn -f $pom javafx:run
$code = $LASTEXITCODE
Write-Log "javafx:run exit=$code"

if ($code -ne 0) {
    Write-Host ""
    Write-Host "ERROR: launch failed (exit $code)"
    Write-Host "Check: $launchLog"
    Write-Host "Check: $(Join-Path $Root 'output\crash.log')"
    Read-Host "Press Enter to exit"
    exit $code
}

exit 0
