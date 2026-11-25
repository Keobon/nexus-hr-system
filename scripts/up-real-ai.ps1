# 백엔드를 실제 Claude API 모드(AI_CLIENT=anthropic)로 다시 띄운다 — 저장소 어디서든: .\scripts\up-real-ai.ps1
# 키는 Windows 사용자 환경변수 NEXUS_ANTHROPIC_API_KEY 에서 읽어 이 스크립트가 도는 동안만 ANTHROPIC_API_KEY 로 넘긴다.
# 키 값은 화면 · 파일 어디에도 쓰지 않고, 끝나면 이 프로세스의 변수를 원래대로 돌린다(셸 · 시스템 변수 · .env 를 바꾸지 않는다).
# 키 이름을 ANTHROPIC_API_KEY 로 두지 않는 이유: 그 이름이면 Claude Code 등 다른 도구도 같은 키로 과금된다.

$ErrorActionPreference = 'Stop'

# VS Code · 터미널을 다시 켜기 전이면 프로세스 환경에 없을 수 있어 사용자 환경변수도 본다
$key = $env:NEXUS_ANTHROPIC_API_KEY
if (-not $key) { $key = [Environment]::GetEnvironmentVariable('NEXUS_ANTHROPIC_API_KEY', 'User') }
if (-not $key) {
    Write-Host '키가 없습니다'
    exit 1
}

$root = Split-Path -Parent $PSScriptRoot
# 스크립트는 부른 셸 안에서 돈다 — 원래 값을 잡아 두었다가 끝나면 그대로 돌려놓는다
$before = @{ ANTHROPIC_API_KEY = $env:ANTHROPIC_API_KEY; AI_CLIENT = $env:AI_CLIENT }
$code = 1
try {
    $env:ANTHROPIC_API_KEY = $key
    $env:AI_CLIENT = 'anthropic'
    Push-Location $root
    docker compose up -d --build backend
    $code = $LASTEXITCODE
} finally {
    Pop-Location -ErrorAction SilentlyContinue
    foreach ($name in $before.Keys) {
        if ($null -eq $before[$name]) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
        else { Set-Item "Env:$name" $before[$name] }
    }
    $key = $null
}

if ($code -ne 0) {
    Write-Host 'docker compose 가 실패했습니다'
    exit $code
}
Write-Host '백엔드를 실제 Claude 모드로 띄웠습니다. 확인: docker compose logs backend | Select-String "AI 비서"'
