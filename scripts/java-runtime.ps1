function Get-LabJava {
    $candidates = New-Object 'System.Collections.Generic.List[string]'
    if ($env:JAVA_HOME) { $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe')) }
    $jdkRoots = @((Join-Path ([Environment]::GetFolderPath('UserProfile')) '.jdks'), (Join-Path $env:ProgramFiles 'Java'))
    foreach ($root in $jdkRoots) {
        if (Test-Path -LiteralPath $root) {
            Get-ChildItem -LiteralPath $root -Directory | Sort-Object Name -Descending | ForEach-Object { $candidates.Add((Join-Path $_.FullName 'bin\java.exe')) }
        }
    }
    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) { $candidates.Add($pathJava.Source) }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            $major = (Get-Item -LiteralPath $candidate).VersionInfo.FileMajorPart
            if ($major -ge 17) { return $candidate }
        }
    }
    if ($pathJava) {
        Write-Warning 'Java 17+ was not found. Falling back to PATH Java; older Java may look blurry on high-DPI screens.'
        return $pathJava.Source
    }
    throw 'Java was not found. Install Java 17+ or set JAVA_HOME to its installation directory.'
}
