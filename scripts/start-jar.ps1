param([Parameter(ValueFromRemainingArguments=$true)][string[]]$AppArgs)
$ErrorActionPreference = 'Stop'
$projectDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Set-Location -LiteralPath $projectDir
. (Join-Path $PSScriptRoot 'java-runtime.ps1')
$javaExe = Get-LabJava
Write-Host "Using Java: $javaExe"
& $javaExe '-Dfile.encoding=UTF-8' -jar (Join-Path $projectDir 'target\lab-equipment-demo.jar') @AppArgs
exit $LASTEXITCODE
