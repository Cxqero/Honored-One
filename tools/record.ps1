# usage: tools/record.ps1 <name> [-Seconds 30] [-Fps 30] [-Width 1280]
# Records the Minecraft window (any javaw.exe window titled "Minecraft...") with Windows.Graphics.Capture through
# ffmpeg's gfxcapture, so it works under shaders and even when the window is covered. Video only.
# Writes .tools/caps/<name>.mkv, then a contact sheet .tools/caps/<name>_sheet.jpg (4 frames a second).
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [int]$Seconds = 30,
    [int]$Fps = 30,
    [int]$Width = 1280
)
$root = Split-Path -Parent $PSScriptRoot
$ff = "$root\.tools\ffmpeg\bin\ffmpeg.exe"
$caps = "$root\.tools\caps"
New-Item -ItemType Directory -Force $caps | Out-Null
$out = "$caps\$Name.mkv"
$src = "gfxcapture=window_exe=javaw\.exe:window_title=^Minecraft:max_framerate=${Fps}:capture_cursor=false,hwdownload,format=bgra,scale=${Width}:-2,format=yuv420p"
& $ff -hide_banner -loglevel error -y -filter_complex $src -t $Seconds -c:v libx264 -preset veryfast -crf 20 $out
if ($LASTEXITCODE -ne 0) { Write-Output "recording failed ($LASTEXITCODE)"; exit 1 }
& $ff -hide_banner -loglevel error -y -i $out -vf "fps=4,scale=320:-2,tile=8x$([Math]::Ceiling($Seconds * 4 / 8))" -frames:v 1 -q:v 3 "$caps\${Name}_sheet.jpg"
Write-Output "wrote $out and ${Name}_sheet.jpg"
