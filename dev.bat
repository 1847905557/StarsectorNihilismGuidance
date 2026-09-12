@echo off
setlocal
rem Restart the VuePress dev server on port 8080. Ports 8081-8090 are also
rem cleaned of this project's leftover dev servers: when 8080 is still held,
rem vite auto-bumps the dev server to the next free port instead of failing.
title dev - StarsectorNihilismGuidance (8080)
cd /d "%~dp0"

rem Launcher resolution: prefer pnpm, fall back to corepack (shipped with Node).
set "PNPM=pnpm"
where pnpm >nul 2>nul
if errorlevel 1 set "PNPM=corepack pnpm"
where %PNPM% >nul 2>nul
if errorlevel 1 (
  echo [dev] ERROR: neither pnpm nor corepack found in PATH.
  pause
  exit /b 1
)

echo [dev] cleaning this project's leftover dev servers on ports 8080-8090 ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='SilentlyContinue'; $marker='StarsectorNihilismGuidance'; $killed=@{}; foreach($p in 8080..8090){ $conns=@(Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue); foreach($c in $conns){ $op=$c.OwningProcess; if($killed.ContainsKey($op)){continue}; $proc=Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $op) -ErrorAction SilentlyContinue; $ours=$false; if($proc -and ($proc.Name -match 'node') -and $proc.CommandLine){ $ours=($proc.CommandLine -match 'vuepress') -and ($proc.CommandLine -match $marker) }; if($ours){ Write-Host ('[dev] kill leftover dev server: pid {0} on port {1}' -f $op,$p); $killed[$op]=1; Stop-Process -Id $op -Force -ErrorAction SilentlyContinue } else { $nm='unknown'; if($proc){$nm=$proc.Name}; Write-Host ('[dev] keep: port {0} held by pid {1} ({2}), not this project' -f $p,$op,$nm) } } }; if($killed.Count -eq 0){ Write-Host '[dev] no leftover dev servers found' }"

echo [dev] starting dev server on port 8080 with: %PNPM%
call %PNPM% docs:dev -- --port 8080

echo.
echo [dev] dev server exited.
pause
