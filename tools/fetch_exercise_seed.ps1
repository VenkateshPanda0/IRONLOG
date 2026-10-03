param([string]$Output = "app/src/main/assets/seed/exercises.json")
$ErrorActionPreference = 'Stop'
$uri = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json'
$target = Join-Path (Get-Location) $Output
New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
Invoke-WebRequest -Uri $uri -OutFile $target
$records = Get-Content -Raw -LiteralPath $target | ConvertFrom-Json
if ($records.Count -lt 600) { throw "Expected at least 600 exercise records, found $($records.Count)." }
Write-Output "Saved $($records.Count) exercise records to $target. Review the Unlicense attribution before bundling."
