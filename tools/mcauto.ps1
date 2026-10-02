# usage: tools/mcauto.ps1 <scenario> [-Timeout 900] [-Width 1280] [-Height 720] [-Keep]
#   e.g. tools/mcauto.ps1 blue        (scenarios are in mod/src/client/java/.../AutoTest.java)
# Windows version of tools/mcauto: runs a scripted AutoTest scenario in the dev client (real GPU) and saves
# screenshots to mod/run/screenshots/. Everything stays inside mod/run/. The test world "autotest" is deleted and
# regenerated from a fixed seed each run (keep it with -Keep). Extra env: GOJO_X, GOJO_Z, GOJO_YAW, GOJO_TIME,
# GOJO_SEED, GOJO_SHADERS=stable|newest.
param(
    [Parameter(Mandatory = $true)][string]$Scenario,
    [int]$Timeout = 900,
    [int]$Width = 1280,
    [int]$Height = 720,
    [switch]$Keep
)
$root = Split-Path -Parent $PSScriptRoot
$run = "$root\mod\run"
New-Item -ItemType Directory -Force "$run\autotest-logs", "$run\saves" | Out-Null
if (-not $Keep) { Remove-Item -Recurse -Force "$run\saves\autotest" -ErrorAction SilentlyContinue }

# first-run screens off, keep running when the window loses focus, no music, fixed window size
$opts = "$run\options.txt"
$want = [ordered]@{ "onboardAccessibility" = "false"; "pauseOnLostFocus" = "false"; "soundCategory_music" = "0.0";
    "skipMultiplayerWarning" = "true"; "tutorialStep" = "none"; "chatOpacity" = "0.0"; "textBackgroundOpacity" = "0.0"; "overrideWidth" = "$Width"; "overrideHeight" = "$Height" }
$lines = New-Object System.Collections.Generic.List[string]
if (Test-Path $opts) { foreach ($l in Get-Content $opts) { if ($l -match ':') { $lines.Add($l) } } }
foreach ($k in $want.Keys) {
    $i = $lines.FindIndex([Predicate[string]]{ param($l) $l.StartsWith("${k}:") })
    if ($i -ge 0) { $lines[$i] = "${k}:$($want[$k])" } else { $lines.Add("${k}:$($want[$k])") }
}
Set-Content -Path $opts -Value $lines -Encoding ascii

$env:GOJO_AUTOTEST = $Scenario
$log = "$run\autotest-logs\client_$Scenario.log"
$p = Start-Process -FilePath "powershell.exe" -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$root\tools\gradle.ps1`"", "runClient", "--console=plain"
if (-not $p.WaitForExit($Timeout * 1000)) {
    Write-Output "timeout after $Timeout s: stopping the client (PID $($p.Id) and its children)"
    taskkill /T /F /PID $p.Id | Out-Null
}
Write-Output "exit $($p.ExitCode) (log: mod/run/autotest-logs/client_$Scenario.log)"
Select-String -Path $log -Pattern "\[autotest\]|Exception|ERROR|\[CHAT\]" | Where-Object { $_.Line -notmatch "OpenAL" } |
    Select-Object -First 80 | ForEach-Object { $_.Line.Substring(0, [Math]::Min(240, $_.Line.Length)) }
