$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$dataDir = Join-Path $projectDir 'runtime\mysql-data'
$pidFile = Join-Path $projectDir 'runtime\mysql.pid'
if (!(Test-Path -LiteralPath $pidFile)) { Write-Host 'No demo MySQL PID file.'; exit 0 }
$demoPid = [int](Get-Content -LiteralPath $pidFile -Raw)
$process = Get-CimInstance Win32_Process -Filter "ProcessId = $demoPid" -ErrorAction SilentlyContinue
if (!$process) { Write-Host 'Demo MySQL is already stopped.'; exit 0 }
if ($process.Name -ne 'mysqld.exe' -or $process.CommandLine -notlike "*$dataDir*") { throw 'PID no longer belongs to this demo MySQL. Nothing was stopped.' }
$adminExe = Join-Path ([IO.Path]::GetDirectoryName($process.ExecutablePath)) 'mysqladmin.exe'
$portMatch = [regex]::Match($process.CommandLine, '--port=(\d+)')
if (!$portMatch.Success) { throw 'Cannot determine demo port. Nothing was stopped.' }
& $adminExe --no-defaults --host=127.0.0.1 "--port=$($portMatch.Groups[1].Value)" --user=root shutdown
if ($LASTEXITCODE -ne 0) { throw 'Demo MySQL shutdown failed.' }
Write-Host 'Demo MySQL stopped. All demo data is preserved in runtime/mysql-data.'
