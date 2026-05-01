$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot\frontend"
if (-not (Test-Path (Join-Path (Get-Location) 'node_modules'))) { npm install }
npm run dev
