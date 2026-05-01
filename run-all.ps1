Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy Bypass', '-File', "$PSScriptRoot\run-backend.ps1"
Start-Process powershell -ArgumentList '-NoExit', '-ExecutionPolicy Bypass', '-File', "$PSScriptRoot\run-frontend.ps1"
