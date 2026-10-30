# Gera o pacote único de produção: backend\target\checklist-auditoria.jar (com o Angular embutido).
# Uso: .\build.ps1            (compila front, roda testes do back e empacota)
#      .\build.ps1 -SemTestes
param([switch]$SemTestes)
$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot

Write-Host '== Front-end (Angular) ==' -ForegroundColor Cyan
Push-Location "$raiz\frontend"
try {
    npm ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci falhou' }
    npx ng build --configuration production
    if ($LASTEXITCODE -ne 0) { throw 'ng build falhou' }
} finally { Pop-Location }

Write-Host '== Back-end (Spring Boot) ==' -ForegroundColor Cyan
Push-Location "$raiz\backend"
try {
    $mvnArgs = @('-B', 'clean', 'package')
    if ($SemTestes) { $mvnArgs += '-DskipTests' }
    .\mvnw.cmd @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw 'Maven falhou' }
} finally { Pop-Location }

Write-Host "Pacote gerado: $raiz\backend\target\checklist-auditoria.jar" -ForegroundColor Green
