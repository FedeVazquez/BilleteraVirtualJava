<#
.SYNOPSIS
    Levanta todo el sistema en esta maquina SIN Docker: SMTP falso + los 5 microservicios.

.DESCRIPTION
    Los servicios con base de datos arrancan con el perfil "h2" (base en memoria: los datos se pierden
    al apagarlos). Requiere haber compilado antes:  .\mvnw.cmd -DskipTests package
    Los logs quedan en la carpeta logs\ y los mails "enviados" en mails.jsonl.
    Para apagar todo:  .\scripts\stop-local.ps1

.PARAMETER JvmArgs
    Argumentos extra para la JVM de cada servicio. Si un servicio falla al arrancar con
    "Unable to establish loopback connection", usar:
        .\scripts\start-local.ps1 -JvmArgs "-Djdk.net.unixdomain.tmpdir=Q:\no-existe"
#>
param([string[]] $JvmArgs = @())

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force $logs | Out-Null
$pids = [ordered]@{}

function Start-Background($name, $exe, $arguments) {
    $process = Start-Process $exe -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logs "$name.log") -RedirectStandardError (Join-Path $logs "$name.err.log")
    $pids[$name] = $process.Id
}

function Wait-Healthy($name, $port, $timeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if ((Invoke-RestMethod "http://localhost:$port/actuator/health" -TimeoutSec 3).status -eq 'UP') {
                Write-Host ("  {0,-16} UP   (puerto {1})" -f $name, $port)
                return
            }
        } catch { Start-Sleep -Milliseconds 1500 }
    }
    Write-Warning "$name no respondio en $timeoutSeconds s. Revisar logs\$name.log"
}

function Start-Service($name, $port, [switch] $WithDatabase) {
    $jar = Join-Path $root "$name\target\$name.jar"
    if (-not (Test-Path $jar)) { throw "Falta $jar. Compilar primero con: .\mvnw.cmd -DskipTests package" }
    $arguments = @($JvmArgs) + @('-Xmx300m', '-jar', $jar)
    if ($WithDatabase) { $arguments += '--spring.profiles.active=h2' }
    Start-Background $name 'java' $arguments
}

Write-Host 'Levantando Digital Money House en local (sin Docker)...'
Start-Background 'fake-smtp' 'python' @('-u', (Join-Path $root 'scripts\fake_smtp.py'), '1025', (Join-Path $root 'mails.jsonl'))

# Eureka primero: los demas servicios se registran en el al arrancar.
Start-Service 'eureka-server' 8761
Wait-Healthy 'eureka-server' 8761

Start-Service 'account-service' 8083 -WithDatabase
Start-Service 'users-service' 8082 -WithDatabase
Start-Service 'auth-service' 8081 -WithDatabase
Start-Service 'api-gateway' 8090
$pids | ConvertTo-Json | Set-Content (Join-Path $logs 'pids.json')

Wait-Healthy 'account-service' 8083
Wait-Healthy 'users-service' 8082
Wait-Healthy 'auth-service' 8081
Wait-Healthy 'api-gateway' 8090

Write-Host ''
Write-Host 'API (gateway):  http://localhost:8090'
Write-Host 'Swagger UI:     http://localhost:8090/swagger-ui.html'
Write-Host 'Eureka:         http://localhost:8761'
Write-Host 'Nota: el gateway tarda hasta 30 s en conocer a los servicios recien registrados en Eureka.'
