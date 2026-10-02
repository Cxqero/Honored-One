# One-click GitHub setup and update for this mod (run it through setup-github.bat in the project folder).
#
# What it does, in order (safe to run again: it only adds or updates what's missing or new):
#   1. Finds the GitHub CLI (or downloads the official build from github.com/cli/cli into .tools\gh)
#   2. Signs you in to GitHub in your browser, if you aren't already (gh keeps the login in %APPDATA%\GitHub CLI)
#   3. Creates the PUBLIC repository (if it doesn't exist yet) and uploads the source
#   4. Turns on Issues and the Wiki, adds a description and topics
#   5. Publishes the jar in checkpoints\ as a Release with the changelog (if that version isn't released yet)
#   6. Publishes the wiki from wiki\ (the very first time GitHub needs one click on "Save page" in your browser)
# It never deletes anything on GitHub and never commits on its own without asking.
param(
    [string]$RepoName = "Honored-One",
    [string]$Description = "Honored One - Gojo moveset mod for Minecraft 1.21.1 (Fabric): Blue, Red, Hollow Purple, the nuke and Unlimited Void.",
    [switch]$SkipRelease,
    [switch]$SkipWiki,
    [switch]$DryRun,         # check everything, upload nothing
    [switch]$Yes             # don't ask before publishing (for updates)
)
# native tools (git, gh) print progress on stderr: errors are checked through $LASTEXITCODE instead
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Say($msg, $color = "Cyan") { Write-Host ""; Write-Host "==> $msg" -ForegroundColor $color }
function Fail($msg) { Write-Host ""; Write-Host "!! $msg" -ForegroundColor Red; exit 1 }
function Ask($question) {
    if ($DryRun) { Write-Host "$question [dry run: yes]"; return $true }
    if ($Yes) { Write-Host "$question [yes]"; return $true }
    $a = Read-Host "$question [y/n]"
    return $a -match '^(y|yes)$'
}

# ------------------------------------------------------------------ tools
Say "Checking git"
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail "Git isn't installed. Get it from https://git-scm.com/download/win and run this again." }
git rev-parse --is-inside-work-tree 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Fail ("This folder isn't a git repository: $root`n" +
          "   Run setup-github.bat from your project folder (the one with the full history, e.g. C:\mods\minecraft).`n" +
          "   A downloaded or zipped copy of the source has no git history, so it can't be uploaded from.")
}
if ($DryRun) { Write-Host "DRY RUN: nothing will be created or uploaded." -ForegroundColor Yellow }

Say "Finding the GitHub CLI"
$gh = Join-Path $root ".tools\gh\bin\gh.exe"
if (-not (Test-Path $gh)) {
    $onPath = Get-Command gh -ErrorAction SilentlyContinue
    if ($onPath) {
        $gh = $onPath.Source
    } else {
        Write-Host "Downloading the official GitHub CLI from github.com/cli/cli ..."
        $rel = Invoke-RestMethod "https://api.github.com/repos/cli/cli/releases/latest" -ErrorAction Stop
        $v = $rel.tag_name.TrimStart("v")
        $zipName = "gh_${v}_windows_amd64.zip"
        $base = "https://github.com/cli/cli/releases/download/v$v"
        $dl = Join-Path $root ".tools\dl"
        New-Item -ItemType Directory -Force $dl | Out-Null
        Invoke-WebRequest "$base/$zipName" -OutFile "$dl\gh.zip" -UseBasicParsing -ErrorAction Stop
        Invoke-WebRequest "$base/gh_${v}_checksums.txt" -OutFile "$dl\gh_checksums.txt" -UseBasicParsing -ErrorAction Stop
        $want = ((Get-Content "$dl\gh_checksums.txt") | Where-Object { $_ -like "*$zipName" }).Split(" ")[0]
        $got = (Get-FileHash "$dl\gh.zip" -Algorithm SHA256).Hash.ToLower()
        if ($want -ne $got) { Fail "The GitHub CLI download didn't match its official checksum. Nothing was installed." }
        Expand-Archive "$dl\gh.zip" -DestinationPath (Join-Path $root ".tools\gh") -Force -ErrorAction Stop
    }
}
Write-Host "Using $gh"

# ------------------------------------------------------------------ sign in
Say "Checking your GitHub login"
& $gh auth status --hostname github.com 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "You'll sign in through your browser. GitHub shows a one-time code here; paste it on the page that opens."
    & $gh auth login --hostname github.com --git-protocol https --web
    if ($LASTEXITCODE -ne 0) { Fail "GitHub sign-in didn't finish. Run this again when you're ready." }
}
$me = & $gh api user | ConvertFrom-Json
$owner = $me.login
$repo = "$owner/$RepoName"
$noreply = "$($me.id)+$owner@users.noreply.github.com"
Write-Host "Signed in as $owner. Repository: https://github.com/$repo"
if (-not (Ask "Publish to https://github.com/$repo as a PUBLIC repository?")) { Fail "Stopped. Nothing was uploaded." }

# git uses the GitHub CLI login for this project only (nothing changes in your global git settings);
# commits use your private GitHub no-reply address, never your real email
$cred = "!'" + ($gh -replace '\\', '/') + "' auth git-credential"
git config credential.helper '""'
git config --add credential.helper $cred
git config user.name $owner
git config user.email $noreply

# ------------------------------------------------------------------ privacy check
Say "Checking what will be uploaded"
$tracked = git ls-files
if ($tracked -match '(^|/)\.env$') { Fail ".env (your API keys) is tracked by git. Remove it from git before publishing." }
$big = git ls-files -z | ForEach-Object { $_ -split "`0" } | Where-Object { $_ -and (Test-Path $_) -and ((Get-Item $_).Length -gt 50MB) }
if ($big) { Fail "These files are over 50 MB, too big for GitHub: $big" }
$dirty = git status --porcelain
if ($dirty) {
    Write-Host $dirty
    if ($DryRun) {
        Write-Host "(dry run: a real run would offer to commit these changes first)"
    } elseif (Ask "There are changes that aren't committed yet. Commit them now so they're included?") {
        git add -A
        git commit -m "Update"
    } else {
        Write-Host "Uploading the last commit only."
    }
}
$authors = git log --format="%an" | Sort-Object -Unique
Write-Host "Commit authors in the history: $($authors -join ', ')"
# the earlier cloud sessions pushed to <you>/gojo-testing; its old history can still hold personal details
& $gh repo view "$owner/gojo-testing" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "Heads-up: the older repository https://github.com/$owner/gojo-testing still exists." -ForegroundColor Yellow
    Write-Host "Its history comes from before the cleanup and may still contain personal details." -ForegroundColor Yellow
    Write-Host "This script won't touch it. To remove it: open it on GitHub -> Settings -> Danger Zone -> Delete." -ForegroundColor Yellow
}

if ($DryRun) {
    $exists = $false
    & $gh repo view $repo 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $exists = $true }
    $version = ((Get-Content "mod\gradle.properties") | Where-Object { $_ -like "mod_version=*" }).Split("=")[1].Trim()
    $jar = Join-Path $root "checkpoints\gojo-limitless-$version.jar"
    Say "Dry run: this is what a real run would do" "Yellow"
    Write-Host ("- repository https://github.com/$repo : " + $(if ($exists) { "exists, push the new commits" } else { "create it (public), then push" }))
    Write-Host ("- release v$version with " + $(if (Test-Path $jar) { "checkpoints\gojo-limitless-$version.jar (SHA-256 " + (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower() + ")" } else { "NO jar found at $jar" }))
    Write-Host ("- wiki: " + (Get-ChildItem (Join-Path $root "wiki") -Filter "*.md").Count + " pages from wiki\")
    Write-Host "- git credential helper for this repo: $(git config --get-all credential.helper)"
    Write-Host "- commits as: $(git config user.name) <$(git config user.email)>"
    exit 0
}

# ------------------------------------------------------------------ repository
Say "Creating or updating the repository"
& $gh repo view $repo 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    & $gh repo create $repo --public --description $Description
    if ($LASTEXITCODE -ne 0) { Fail "GitHub didn't create the repository." }
}
& $gh repo edit $repo --enable-issues --enable-wiki --description $Description `
    --add-topic minecraft --add-topic minecraft-mod --add-topic fabric --add-topic fabricmc --add-topic jujutsu-kaisen --add-topic gojo | Out-Null
$url = "https://github.com/$repo.git"
$remote = git remote 2>$null
if ($remote -contains "origin") { git remote set-url origin $url } else { git remote add origin $url }
git push -u origin main
if ($LASTEXITCODE -ne 0) { Fail "Uploading the source failed (see above)." }

# ------------------------------------------------------------------ release
$version = ((Get-Content "mod\gradle.properties") | Where-Object { $_ -like "mod_version=*" }).Split("=")[1].Trim()
if (-not $SkipRelease) {
    Say "Release v$version"
    $jar = Join-Path $root "checkpoints\gojo-limitless-$version.jar"
    & $gh release view "v$version" --repo $repo 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "v$version is already released."
    } elseif (-not (Test-Path $jar)) {
        Write-Host "No checkpoints\gojo-limitless-$version.jar yet: build it first (tools\gradle.ps1 build, then copy it there). Skipping." -ForegroundColor Yellow
    } else {
        # release notes = the changelog + the jar's fingerprint (the build is reproducible: building this tag gives
        # the exact same file, so anyone can check the download against the source)
        $sha = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLower()
        $notes = Get-ChildItem $root -Filter "*Limitless v$version.txt" | Select-Object -First 1
        $body = if ($notes) { [IO.File]::ReadAllText($notes.FullName) } else { "Honored One v$version" }
        $body += "`n`n### Verify this download`n- SHA-256 of ``gojo-limitless-$version.jar``: ``$sha```n" +
                 "- The build is reproducible: build this tag yourself (``cd mod``, ``.\gradlew.bat build``) and the jar " +
                 "in ``mod\build\libs`` has the same SHA-256. Same number = same file.`n"
        $notesFile = Join-Path $root ".tools\release-notes.md"
        [IO.File]::WriteAllText($notesFile, $body, (New-Object Text.UTF8Encoding($false)))
        & $gh release create "v$version" $jar --repo $repo --title "Honored One v$version" --notes-file $notesFile
        if ($LASTEXITCODE -ne 0) { Fail "Creating the release failed (see above)." }
    }
}

# ------------------------------------------------------------------ wiki
if (-not $SkipWiki) {
    Say "Wiki"
    $wikiUrl = "https://github.com/$repo.wiki.git"
    git ls-remote $wikiUrl 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "GitHub only creates a wiki after its first page is saved on the website." -ForegroundColor Yellow
        Write-Host "Opening it in your browser: just click the green 'Save page' button (leave the text as it is)."
        Start-Process "https://github.com/$repo/wiki/_new"
        Read-Host "Press Enter here after you've clicked 'Save page'"
    }
    $tmp = Join-Path $root ".tools\wiki-push"
    if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
    git clone -q $wikiUrl $tmp
    if ($LASTEXITCODE -ne 0) { Fail "Couldn't open the wiki. Make sure you clicked 'Save page', then run this again." }
    Get-ChildItem $tmp -File | Where-Object { $_.Name -like "*.md" } | Remove-Item
    foreach ($f in Get-ChildItem (Join-Path $root "wiki") -Filter "*.md") {
        $text = [IO.File]::ReadAllText($f.FullName)
        $text = $text.Replace("{{REPO}}", $repo).Replace("{{REPO_NAME}}", $RepoName).Replace("{{VERSION}}", $version)
        [IO.File]::WriteAllText((Join-Path $tmp $f.Name), $text, (New-Object Text.UTF8Encoding($false)))
    }
    Push-Location $tmp
    git config credential.helper '""'
    git config --add credential.helper $cred
    git config user.name $owner
    git config user.email $noreply
    git add -A
    git commit -q -m "Wiki for v$version" 2>$null
    git push -q origin HEAD
    $pushed = $LASTEXITCODE
    Pop-Location
    Remove-Item -Recurse -Force $tmp
    if ($pushed -ne 0) { Fail "Uploading the wiki failed (see above)." }
}

Say "One last click (protects your wiki)" "Yellow"
Write-Host "On a public repository anyone with a GitHub account can edit the wiki. In the settings page that opens,"
Write-Host "scroll to Features -> Wikis and tick 'Restrict editing to collaborators only'."
Start-Process "https://github.com/$repo/settings"

Say "Done!" "Green"
Write-Host "Repository: https://github.com/$repo"
Write-Host "Wiki:       https://github.com/$repo/wiki"
Write-Host "Releases:   https://github.com/$repo/releases"
Start-Process "https://github.com/$repo"
