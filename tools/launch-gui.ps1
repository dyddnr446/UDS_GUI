# Internal launcher called by run.bat only. Do not document as a user entry point.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
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

function Wait-IfInteractive {
    if ($env:UDS_FROM_BAT -eq "1") { return }
    Read-Host "Press Enter to exit" | Out-Null
}

Set-Content -Path $launchLog -Value ("===== UDS launch {0} =====" -f (Get-Date)) -Encoding UTF8
Write-Log "project=$Root"
Write-Log "os=$([System.Environment]::OSVersion.VersionString)"

foreach ($name in @("src", "test", "doc", "target", ".mvn", "mvnw", "mvnw.cmd", ".cursorrules", "pom.xml")) {
    $p = Join-Path $Root $name
    if (Test-Path $p) { attrib +H $p 2>$null | Out-Null }
}

$sessionDir = Join-Path $outDir ".session"
if (-not (Test-Path $sessionDir)) { New-Item -ItemType Directory -Path $sessionDir | Out-Null }
attrib +H $sessionDir 2>$null | Out-Null

function Get-JavaMajor([string]$javaExe) {
    # java -version writes to stderr; Stop mode must not treat that as fatal.
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
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) {
        $j = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-Path $j) { [void]$candidates.Add($j) }
    }
    $roots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk-25*" } |
            ForEach-Object {
                $j = Join-Path $_.FullName "bin\java.exe"
                if (Test-Path $j) { [void]$candidates.Add($j) }
            }
    }
    $where = Get-Command java -ErrorAction SilentlyContinue
    if ($where) { [void]$candidates.Add($where.Source) }

    foreach ($javaExe in ($candidates | Select-Object -Unique)) {
        $major, $ver = Get-JavaMajor $javaExe
        if ($major -ge 25) {
            $jdkHome = Split-Path (Split-Path $javaExe -Parent) -Parent
            return $jdkHome, $ver
        }
    }
    return $null, $null
}

function Resolve-Maven([string]$projectRoot) {
    $where = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($where) { return $where.Source }

    $dirs = Get-ChildItem $env:USERPROFILE -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "apache-maven-*" }
    foreach ($d in $dirs) {
        $m = Join-Path $d.FullName "bin\mvn.cmd"
        if (Test-Path $m) { return $m }
    }

    if (Test-Path "C:\apache-maven-3.9.9\bin\mvn.cmd") {
        return "C:\apache-maven-3.9.9\bin\mvn.cmd"
    }

    $wrapper = Join-Path $projectRoot "mvnw.cmd"
    if (Test-Path $wrapper) { return $wrapper }

    return $null
}

if (-not $IsWindows -and $env:OS -ne "Windows_NT") {
    Write-Host "ERROR: Windows only. This assignment GUI launcher does not support this OS."
    Write-Log "ERROR: unsupported OS"
    Wait-IfInteractive
    exit 1
}

$javaHome, $javaVer = Resolve-Jdk25
if (-not $javaHome) {
    Write-Host "ERROR: JDK 25+ not found. Install Temurin 25 or set JAVA_HOME."
    Write-Log "ERROR: JDK 25+ not found"
    Wait-IfInteractive
    exit 1
}

$mvn = Resolve-Maven $Root
if (-not $mvn) {
    Write-Host "ERROR: Maven not found. Keep project mvnw.cmd or install Maven."
    Write-Log "ERROR: Maven not found"
    Wait-IfInteractive
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
    Wait-IfInteractive
    exit $code
}

exit 0
