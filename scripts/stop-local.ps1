<#
.SYNOPSIS
    Apaga los procesos que levanto start-local.ps1 (lee sus PID de logs\pids.json).
#>
$root = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $root 'logs\pids.json'
if (-not (Test-Path $pidFile)) {
    Write-Host 'No hay nada para apagar (no existe logs\pids.json).'
    return
}
$pids = Get-Content $pidFile -Raw | ConvertFrom-Json
foreach ($entry in $pids.PSObject.Properties) {
    $process = Get-Process -Id $entry.Value -ErrorAction SilentlyContinue
    # Se verifica el nombre para no matar otro proceso que haya reutilizado el mismo PID.
    if ($process -and $process.ProcessName -in 'java', 'python') {
        Stop-Process -Id $entry.Value -Force
        Write-Host "  $($entry.Name) detenido"
    }
}
Remove-Item $pidFile
