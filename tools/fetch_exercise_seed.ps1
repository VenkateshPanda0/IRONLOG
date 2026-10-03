param([string]$Output = "app/src/main/assets/seed/exercises.json")
$ErrorActionPreference = 'Stop'
$uri = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json'
$target = Join-Path (Get-Location) $Output
New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
Invoke-WebRequest -Uri $uri -OutFile $target
Write-Output "Saved licensed exercise source to $target. Review the Unlicense attribution before bundling."
