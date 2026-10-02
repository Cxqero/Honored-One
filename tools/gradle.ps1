# usage: tools/gradle.ps1 <task> [args...]     e.g. tools/gradle.ps1 build, tools/gradle.ps1 runClient
# Runs the project's Gradle with everything kept inside this folder: Gradle itself in .tools/, its caches (Minecraft,
# Fabric, libraries) in .tools/gradle-home. Nothing is written to %USERPROFILE%\.gradle.
$root = Split-Path -Parent $PSScriptRoot
$gradle = Get-ChildItem "$root\.tools" -Directory -Filter "gradle-8.*" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $gradle) { Write-Error "Gradle not found in .tools/ (see CLAUDE.md, Building)"; exit 1 }
$env:GRADLE_USER_HOME = "$root\.tools\gradle-home"
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk-2*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
Push-Location "$root\mod"
try { & "$($gradle.FullName)\bin\gradle.bat" @args; $code = $LASTEXITCODE } finally { Pop-Location }
exit $code
