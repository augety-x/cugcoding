$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'

Write-Host '==> Updating backend and frontend build artifacts...' -ForegroundColor Cyan

Write-Host '==> Backend: running tests' -ForegroundColor Cyan
Push-Location $backend
mvn test -q
Pop-Location

Write-Host '==> Frontend: installing dependencies if needed' -ForegroundColor Cyan
Push-Location $frontend
if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
  npm install
}
Write-Host '==> Frontend: building production bundle' -ForegroundColor Cyan
npm run build
Pop-Location

Write-Host ''
Write-Host 'Done.' -ForegroundColor Green
Write-Host 'If you are using dev mode, keep these two terminals running:' -ForegroundColor Yellow
Write-Host '  Backend : cd backend; mvn spring-boot:run'
Write-Host '  Frontend: cd frontend; npm run dev'
Write-Host ''
Write-Host 'If you want to preview the latest built frontend, run:' -ForegroundColor Yellow
Write-Host '  cd frontend; npm run preview'
