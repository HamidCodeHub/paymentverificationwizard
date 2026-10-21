# Puts the two model files this service needs into .\models\ :
#   models\clip-vit-base-patch32-vision.onnx   copied from ..\wizard\models if present, else downloaded (~350 MB)
#   models\tessdata\fas.traineddata            Tesseract's Persian model, tessdata_best (~3 MB)
# and checks that Tesseract itself is installed.
#
#   .\scripts\download-models.ps1
#
# Run from the project root.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # Invoke-WebRequest is far slower with the progress bar on

$root      = Split-Path -Parent $PSScriptRoot
$models    = Join-Path $root "models"
$tessdata  = Join-Path $models "tessdata"
New-Item -ItemType Directory -Force -Path $tessdata | Out-Null

# --- CLIP -------------------------------------------------------------------------------------------
$clip    = Join-Path $models "clip-vit-base-patch32-vision.onnx"
$sibling = Join-Path (Split-Path -Parent $root) "wizard\models\clip-vit-base-patch32-vision.onnx"
if (Test-Path $clip) {
    Write-Host "CLIP model already present."
} elseif (Test-Path $sibling) {
    Write-Host "Copying the CLIP model from the style-match project ..."
    Copy-Item $sibling $clip
} else {
    Write-Host "Downloading the CLIP vision encoder (~350 MB) ..."
    Invoke-WebRequest -Uri "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model.onnx" -OutFile $clip
}
if ((Get-Item $clip).Length -lt 20MB) { Remove-Item $clip; throw "CLIP download looks truncated; retry." }

# --- Tesseract Persian model -------------------------------------------------------------------------
$fas = Join-Path $tessdata "fas.traineddata"
if (Test-Path $fas) {
    Write-Host "fas.traineddata already present."
} else {
    Write-Host "Downloading fas.traineddata (tessdata_best) ..."
    Invoke-WebRequest -Uri "https://github.com/tesseract-ocr/tessdata_best/raw/main/fas.traineddata" -OutFile $fas
}
if ((Get-Item $fas).Length -lt 1MB) { Remove-Item $fas; throw "fas.traineddata looks truncated; retry." }

# --- Tesseract engine --------------------------------------------------------------------------------
$exe = (Get-Command tesseract -ErrorAction SilentlyContinue).Source
if (-not $exe -and (Test-Path "C:\Program Files\Tesseract-OCR\tesseract.exe")) { $exe = "C:\Program Files\Tesseract-OCR\tesseract.exe" }
if ($exe) {
    Write-Host "Tesseract found: $exe  ($(& $exe --version 2>&1 | Select-Object -First 1))"
    if (-not (Get-Command tesseract -ErrorAction SilentlyContinue)) {
        Write-Host "It is not on PATH, so start the service with:  --gate.ocr.command=`"$exe`""
    }
} else {
    Write-Host ""
    Write-Host "Tesseract is not installed. Install it with:"
    Write-Host "    winget install UB-Mannheim.TesseractOCR"
    Write-Host "(or the installer from https://github.com/UB-Mannheim/tesseract/wiki), then re-run this script."
    exit 1
}

Write-Host ""
Write-Host "Ready. Check accuracy on the references:  mvn spring-boot:run `"-Dspring-boot.run.arguments=--verify`""
