# Uploads ONE service to its own Oracle Always Free micro machine (VM.Standard.E2.1.Micro, 1 GB).
#
#   .\deploy\oracle-micro\upload.ps1 -Role receipts -Server 1.2.3.4
#   .\deploy\oracle-micro\upload.ps1 -Role match    -Server 5.6.7.8
#   ... -SkipModels      code/images only (the model is already on that server)
#
# Uses only what ships with Windows 10/11: tar.exe, ssh.exe, scp.exe. Safe to re-run after any change:
# sources and reference images are replaced, the model and embedding caches are kept, .env is never touched.

param(
    [Parameter(Mandatory = $true)][ValidateSet("receipts", "match")][string]$Role,
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
$models = Join-Path $gate "models"
$int8 = "clip-vit-base-patch32-vision-int8.onnx"

if (-not $SkipModels -and -not (Test-Path (Join-Path $models $int8))) {
    throw "Missing $models\$int8 - create it once with:  py scripts\quantize-model.py   (see deploy\oracle-micro\MICRO.md)"
}

$tmp = Join-Path $env:TEMP "pantea-upload"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

# Pack one folder (".") or only the listed items inside it.
function Pack([string]$Name, [string]$Dir, [string[]]$Exclude = @(), [string[]]$Items = @(".")) {
    if (-not (Test-Path $Dir)) { throw "Missing folder: $Dir" }
    $out = Join-Path $tmp "$Name.tgz"
    $tarArgs = @("-czf", $out)
    foreach ($e in $Exclude) { $tarArgs += "--exclude=$e" }
    $tarArgs += @("-C", $Dir) + $Items
    & tar.exe @tarArgs
    if ($LASTEXITCODE -ne 0) { throw "tar failed for $Dir" }
    Write-Host ("  {0,-26} {1,8:N1} MB" -f $Name, ((Get-Item $out).Length / 1MB))
}

$sourceExcludes = @("target", ".idea", ".git", "models", "references", "themes", "node_modules", "*.npz",
                    "probe_scores.json", "*.log", "bench", "wordpress-plugin", "_to_delete", ".venv*")

Write-Host "Packing for '$Role' ..."
Pack "stack" (Join-Path $gate "deploy\oracle-micro")
if ($Role -eq "receipts") {
    Pack "app" $gate $sourceExcludes
    Pack "data-references" (Join-Path $gate "references")
    if (-not $SkipModels) { Pack "models" $models @() @($int8, "tessdata") }
} else {
    Pack "app" $wizard $sourceExcludes
    Pack "data-themes" (Join-Path $Pantea "themes\new")
    if (-not $SkipModels) { Pack "models" $models @() @($int8) }
}

# The unpacking runs on the server. Written with LF line endings so bash does not choke on CR.
$appDir = if ($Role -eq "receipts") { "paymentverificationwizard" } else { "wizard" }
$install = @'
set -e
U=/tmp/pantea-upload
P=~/pantea
ROLE=__ROLE__
APP=__APP__
mkdir -p $P/stack $P/data/models $P/data/themes $P/data/references
rm -rf "$P/$APP" && mkdir -p "$P/$APP" && tar -xzf "$U/app.tgz" -C "$P/$APP"
rm -rf $U/stack && mkdir -p $U/stack && tar -xzf "$U/stack.tgz" -C $U/stack
cp $U/stack/compose.$ROLE.yml $P/stack/docker-compose.yml
cp $U/stack/Caddyfile $U/stack/.env.example $U/stack/server-setup.sh $P/stack/
sed -i 's/\r$//' $P/stack/* $P/stack/.env.example
for d in themes references; do
  if [ -f "$U/data-$d.tgz" ]; then
    rm -rf "$P/data/$d" && mkdir -p "$P/data/$d" && tar -xzf "$U/data-$d.tgz" -C "$P/data/$d"
  fi
done
if [ -f "$U/models.tgz" ]; then tar -xzf "$U/models.tgz" -C "$P/data/models"; fi
chmod -R a+rwX $P/data/models
chmod -R a+rX $P/data/themes $P/data/references
rm -rf "$U"
echo
echo "Uploaded '$ROLE' to $P:"
du -sh $P/$APP $P/data/* 2>/dev/null
'@
$install = $install.Replace("__ROLE__", $Role).Replace("__APP__", $appDir)
[IO.File]::WriteAllText((Join-Path $tmp "install.sh"), $install.Replace("`r`n", "`n"))

Write-Host "Uploading to $target ..."
& ssh.exe @sshOpts $target "rm -rf /tmp/pantea-upload && mkdir -p /tmp/pantea-upload"
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
