# Uploads both services, the shared models and the reference images to the Oracle machine.
#
#   .\deploy\oracle\upload.ps1 -Server 1.2.3.4
#   .\deploy\oracle\upload.ps1 -Server 1.2.3.4 -SkipModels    # code/images only (models already on the server)
#
# Uses only what ships with Windows 10/11: tar.exe, ssh.exe, scp.exe. Safe to re-run after any change:
# sources and reference images are replaced, models and embedding caches are kept, .env is never touched.

param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$User = "ubuntu",
    [string]$Key = "$HOME\.ssh\oracle_pantea",
    [string]$Pantea = "E:\Work\wordpress\Pantea",
    [switch]$SkipModels
)

$ErrorActionPreference = "Stop"
$target = "$User@$Server"
$sshOpts = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")
$gate = Join-Path $Pantea "paymentverificationwizard"
$wizard = Join-Path $Pantea "wizard"

$tmp = Join-Path $env:TEMP "pantea-upload"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Pack([string]$Name, [string]$Dir, [string[]]$Exclude = @()) {
    if (-not (Test-Path $Dir)) { throw "Missing folder: $Dir" }
    $out = Join-Path $tmp "$Name.tgz"
    $tarArgs = @("-czf", $out)
    foreach ($e in $Exclude) { $tarArgs += "--exclude=$e" }
    $tarArgs += @("-C", $Dir, ".")
    & tar.exe @tarArgs
    if ($LASTEXITCODE -ne 0) { throw "tar failed for $Dir" }
    Write-Host ("  {0,-26} {1,8:N1} MB" -f $Name, ((Get-Item $out).Length / 1MB))
}

$sourceExcludes = @("target", ".idea", ".git", "models", "references", "node_modules", "*.npz",
                    "probe_scores.json", "*.log", "_to_delete", ".venv*")

Write-Host "Packing ..."
Pack "wizard" $wizard ($sourceExcludes + @("themes"))
Pack "paymentverificationwizard" $gate $sourceExcludes
Pack "themes" (Join-Path $Pantea "themes\new")
Pack "references" (Join-Path $gate "references")
if (-not $SkipModels) {
    # One copy of the CLIP model serves both services; tessdata is the receipt gate's Persian OCR model.
    Pack "models" (Join-Path $gate "models") @("*.json", "*.tmp")
}

# The unpacking runs on the server. Written with LF line endings so bash does not choke on CR.
$install = @'
set -e
U=/tmp/pantea-upload
P=~/pantea
mkdir -p $P/stack $P/data/models $P/data/themes $P/data/references
for app in wizard paymentverificationwizard; do
  rm -rf "$P/$app" && mkdir -p "$P/$app" && tar -xzf "$U/$app.tgz" -C "$P/$app"
done
cp $P/paymentverificationwizard/deploy/oracle/{docker-compose.yml,Caddyfile,.env.example,server-setup.sh} $P/stack/
sed -i 's/\r$//' $P/stack/* $P/stack/.env.example
for d in themes references; do
  rm -rf "$P/data/$d" && mkdir -p "$P/data/$d" && tar -xzf "$U/$d.tgz" -C "$P/data/$d"
done
if [ -f "$U/models.tgz" ]; then tar -xzf "$U/models.tgz" -C "$P/data/models"; fi
chmod -R a+rwX $P/data/models
chmod -R a+rX $P/data/themes $P/data/references
rm -rf "$U"
echo
echo "Uploaded to $P:"
du -sh $P/wizard $P/paymentverificationwizard $P/data/* 2>/dev/null
'@
[IO.File]::WriteAllText((Join-Path $tmp "install.sh"), $install.Replace("`r`n", "`n"))

Write-Host "Uploading to $target ..."
& ssh.exe @sshOpts $target "mkdir -p /tmp/pantea-upload"
if ($LASTEXITCODE -ne 0) { throw "Cannot reach $target over SSH. Check the IP, the key path and that port 22 is open." }
& scp.exe @sshOpts (Get-ChildItem $tmp | ForEach-Object FullName) "${target}:/tmp/pantea-upload/"
if ($LASTEXITCODE -ne 0) { throw "scp failed" }
& ssh.exe @sshOpts $target "bash /tmp/pantea-upload/install.sh"
if ($LASTEXITCODE -ne 0) { throw "Unpacking on the server failed" }

Remove-Item -Recurse -Force $tmp
Write-Host ""
Write-Host "Next, on the server:  ssh -i $Key $target"
Write-Host "  first time only:    bash ~/pantea/stack/server-setup.sh   (then log out and back in)"
Write-Host "  then:               cd ~/pantea/stack && docker compose up -d --build"
