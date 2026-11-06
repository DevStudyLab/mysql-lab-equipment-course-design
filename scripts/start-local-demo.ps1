param([string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 5.7\bin', [int]$Port = 13318, [switch]$CheckOnly, [string]$JavaExe, [string]$JarPath)
$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectDir
$serverExe = Join-Path $MySqlBin 'mysqld.exe'
$clientExe = Join-Path $MySqlBin 'mysql.exe'
if (!(Test-Path -LiteralPath $serverExe)) { throw "MySQL not found. Pass -MySqlBin with your MySQL bin directory. See README.md." }
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
if (!$JavaExe) { $JavaExe = Get-LabJava }
Write-Host "Using Java: $javaExe"
$jar = if ($JarPath) { $JarPath } else { Join-Path $projectDir 'target\lab-equipment-demo.jar' }
if (!(Test-Path -LiteralPath $jar)) { throw 'Build the project with mvn package first.' }
$runtimeDir = Join-Path $projectDir 'runtime'
$dataDir = Join-Path $runtimeDir 'mysql-data'
$pidFile = Join-Path $runtimeDir 'mysql.pid'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    & $serverExe --no-defaults --initialize-insecure "--basedir=$([IO.Path]::GetDirectoryName($MySqlBin))" "--datadir=$dataDir" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed. See console output. No existing databases were changed.' }
}
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
$existing = $null
if (Test-Path -LiteralPath $pidFile) { $existing = Get-CimInstance Win32_Process -Filter "ProcessId = $([int](Get-Content -LiteralPath $pidFile -Raw))" -ErrorAction SilentlyContinue }
if ($listener) {
    if (!$existing -or $existing.CommandLine -notlike "*$dataDir*" -or @($listener.OwningProcess) -notcontains [int]$existing.ProcessId) { throw "Port $Port is occupied by a different process. This script will not stop it." }
} else {
    $arguments = @('--no-defaults', "--basedir=`"$([IO.Path]::GetDirectoryName($MySqlBin))`"", "--datadir=`"$dataDir`"", "--port=$Port", '--bind-address=127.0.0.1', '--explicit_defaults_for_timestamp=1', '--log-error=demo.err')
    $process = Start-Process -FilePath $serverExe -ArgumentList $arguments -WindowStyle Hidden -PassThru
    [IO.File]::WriteAllText($pidFile, [string]$process.Id)
}
$ready = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    $ErrorActionPreference = 'Continue'
    & $clientExe --no-defaults --host=127.0.0.1 "--port=$Port" --user=root --execute='SELECT 1' 2>$null | Out-Null
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -eq 0) { $ready=$true; break }
    Start-Sleep -Milliseconds 500
}
if (!$ready) { throw "MySQL did not become ready. Inspect $dataDir\demo.err" }
# This account and database exist only in this script's isolated localhost instance.
& $clientExe --no-defaults --host=127.0.0.1 "--port=$Port" --user=root --execute="CREATE DATABASE IF NOT EXISTS lab_equipment_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'lab_demo'@'localhost' IDENTIFIED BY 'LabDemoLocal@123'; GRANT ALL PRIVILEGES ON lab_equipment_demo.* TO 'lab_demo'@'localhost';"
if ($LASTEXITCODE -ne 0) { throw 'Could not prepare demo database.' }
$env:DB_URL = "jdbc:mysql://127.0.0.1:$Port/lab_equipment_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8"
$env:DB_USER = 'lab_demo'
$env:DB_PASSWORD = 'LabDemoLocal@123'
$tableCount = & $clientExe --no-defaults --host=127.0.0.1 "--port=$Port" --user=root --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='lab_equipment_demo'"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect demo schema.' }
if ([int]$tableCount -eq 0) {
    & $javaExe '-Dfile.encoding=UTF-8' -jar $jar --init
    if ($LASTEXITCODE -ne 0) { throw 'Schema initialization failed. See output above.' }
}
Write-Host 'Demo ready. Login: admin / Demo@123. Close the app, then use stop-demo.bat to stop its isolated MySQL.'
if ($CheckOnly) { & $javaExe '-Dfile.encoding=UTF-8' -jar $jar --check }
else { & $javaExe '-Dfile.encoding=UTF-8' -jar $jar }
if ($LASTEXITCODE -ne 0) { throw 'Application exited with an error.' }
