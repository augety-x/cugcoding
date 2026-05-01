$ErrorActionPreference = 'Stop'
Set-Location "$PSScriptRoot\backend"
mvn spring-boot:run
