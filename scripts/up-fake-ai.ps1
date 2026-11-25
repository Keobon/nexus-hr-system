# 백엔드를 키 없이 규칙 기반 가짜 AI 모드(AI_CLIENT=fake)로 다시 띄운다 — .\scripts\up-fake-ai.ps1
# 컨테이너를 다시 만들어 실제 모드 때 넘긴 키가 컨테이너 설정에 남지 않게 한다. 셸 변수는 끝나면 원래대로.

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$before = @{ ANTHROPIC_API_KEY = $env:ANTHROPIC_API_KEY; AI_CLIENT = $env:AI_CLIENT }
$code = 1
try {
    Remove-Item Env:ANTHROPIC_API_KEY -ErrorAction SilentlyContinue
    $env:AI_CLIENT = 'fake'
    Push-Location $root
    docker compose up -d --build backend
    $code = $LASTEXITCODE
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    foreach ($name in $before.Keys) {
        if ($null -eq $before[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$name" $before[$name] }
    }
}

if ($code -ne 0) {
    Write-Host 'docker compose 가 실패했습니다'
    exit $code
}
Write-Host '백엔드를 가짜 AI 모드로 띄웠습니다.'
