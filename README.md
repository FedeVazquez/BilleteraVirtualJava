# Digital Money House — Backend

Billetera virtual. Desafío profesional de la especialización Backend, con arquitectura de
microservicios. **Sprint 1:** registro con cuenta digital (CVU y alias), login, logout y, como
opcionales, verificación de email y recupero de contraseña.

## Stack

Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0 (Eureka, Gateway, OpenFeign) · Spring Security + JWT
(jjwt) · Spring Data JPA · MySQL 8.4 · springdoc-openapi (Swagger) · JUnit 5 + Mockito · Docker Compose

## Estructura

```
eureka-server/     Service discovery (:8761)
api-tests/         Tests automatizados de la API con RestAssured (corren contra el sistema levantado)
api-gateway/       Punto de entrada: rutas y validación de token (:8090)
auth-service/      Login, logout, JWT (:8081)
users-service/     Usuarios, roles, verificación de email, recupero de contraseña (:8082)
account-service/   Cuenta digital: CVU y alias (:8083)
docker/            Script de inicialización de MySQL
scripts/           Entorno local sin Docker y ejecución de los casos de prueba
docs/              Infraestructura, testing e informe del sprint
```

## Cómo levantarlo

### Opción A — Todo con Docker

```bash
docker compose up -d --build
```

La API queda en `http://localhost:8090`, los mails en `http://localhost:8025` (MailHog) y el panel
de Eureka en `http://localhost:8761`. Para cambiar secretos o puertos, copiar `.env.example` a `.env`.

### Opción B — Desde Eclipse, con la infraestructura en Docker

1. `docker compose up -d mysql mailhog`
2. En Eclipse: **File → Import → Maven → Existing Maven Projects**, elegir esta carpeta y marcar
   los 6 proyectos. El JRE del workspace tiene que ser 17 o superior.
3. Ejecutar con **Run As → Java Application** la clase `*Application` de cada módulo, empezando
   por `EurekaServerApplication` y dejando `ApiGatewayApplication` para el final.

### Opción C — Sin Docker (base en memoria)

```powershell
.\mvnw.cmd -DskipTests package
.\scripts\start-local.ps1      # levanta SMTP falso + los 5 servicios con el perfil h2
.\scripts\stop-local.ps1       # apaga todo
```

Desde Eclipse es lo mismo que la opción B sin el paso 1, agregando
`-Dspring.profiles.active=h2` en *Run Configurations → Arguments → VM arguments* de
`auth-service`, `users-service` y `account-service`. Los datos se pierden al apagar los servicios.

## API

Swagger UI unificado: `http://localhost:8090/swagger-ui.html` (arriba a la derecha se elige el servicio).

| Método y ruta | Auth | Descripción | Respuestas |
|---|---|---|---|
| `POST /users/register` | — | Registra al usuario y crea su cuenta (CVU y alias). Envía el código de verificación. | 201 · 400 · 500 |
| `POST /auth/login` | — | Devuelve el token JWT. | 200 · 400 contraseña incorrecta · 403 email sin verificar · 404 usuario inexistente · 500 |
| `POST /user/logout` | Bearer | Invalida el token. | 200 · 401 · 500 |
| `POST /auth/verify-email` | — | Verifica el email con el código de 6 dígitos. | 200 · 400 · 404 |
| `POST /auth/verify-email/resend` | — | Reenvía el código. | 200 · 400 · 404 |
| `POST /auth/forgot-password` | — | Envía el mail con el link de recupero. | 200 |
| `POST /auth/reset-password` | — | Cambia la contraseña con el token del mail. | 200 · 400 |
| `GET /auth/reset-password.html` | — | Pantalla para ingresar y repetir la nueva contraseña. | 200 |
| `GET /auth/validate` | Bearer | Datos del dueño del token (lo usa el gateway). | 200 · 401 |
| `GET /users/{id}` | Bearer | Datos del usuario (el propio, o cualquiera si es admin). | 200 · 401 · 403 · 404 |
| `PATCH /users/{id}` | Bearer | Editar nombre, apellido y teléfono. | 200 · 400 · 403 · 404 |
| `GET /accounts/user/{userId}` | Bearer | Cuenta digital del usuario (para conocer el id de cuenta). | 200 · 401 · 403 · 404 |
| `GET /accounts/{id}` | Bearer | Cuenta con su dinero disponible. | 200 · 400 · 403 · 500 |
| `PATCH /accounts/{id}` | Bearer | Cambiar el alias. | 200 · 400 · 403 · 409 alias en uso |
| `GET /accounts/{id}/transactions` | Bearer | Últimos 5 movimientos, del más reciente al más antiguo. | 200 · 400 · 403 · 500 |
| `GET /accounts/{id}/cards` | Bearer | Tarjetas de la cuenta (lista vacía si no tiene). | 200 · 403 · 500 |
| `GET /accounts/{id}/cards/{cardId}` | Bearer | Detalle de una tarjeta. | 200 · 403 · 404 |
| `POST /accounts/{id}/cards` | Bearer | Asociar una tarjeta. Solo se guardan los últimos 4 dígitos. | 201 · 400 · 403 · 409 ya asociada |
| `DELETE /accounts/{id}/cards/{cardId}` | Bearer | Eliminar una tarjeta. | 200 · 403 · 404 |

Todos los errores tienen el mismo formato:

```json
{ "timestamp": "2026-10-02T19:52:47Z", "status": 400, "error": "Bad Request",
  "message": "Datos de entrada inválidos", "path": "/users/register",
  "details": ["email: El email no tiene un formato válido"] }
```

Ejemplo de uso:

```bash
curl -X POST http://localhost:8090/users/register -H "Content-Type: application/json" \
  -d '{"firstName":"Juan","lastName":"Perez","dni":"30123456","email":"juan@mail.com","phone":"+5491122334455","password":"Clave1234"}'
```

El código de verificación llega a MailHog (`http://localhost:8025`) o, sin Docker, a `mails.jsonl`.

## Tests

```bash
./mvnw verify                          # tests unitarios y de integración (H2, sin infraestructura)
./mvnw -pl api-tests test -DskipApiTests=false                 # automatización RestAssured (ver api-tests/README.md)
./mvnw -pl api-tests test -DskipApiTests=false -Dgroups=smoke  # solo la suite de humo
python scripts/run_test_suite.py       # casos de prueba de la planilla, contra el sistema levantado
python scripts/run_test_suite.py --suite smoke
python scripts/build_test_sheet.py     # regenera docs/testing/casos-de-prueba.xlsx
```

Con Docker, agregar `--mailhog http://localhost:8025` a `run_test_suite.py`.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/informe-sprint-1.pdf](docs/informe-sprint-1.pdf) | Qué se hizo, y cuándo y para qué se usó cada herramienta |
| [docs/infraestructura.md](docs/infraestructura.md) | Diseño de la infraestructura, red y componentes |
| [docs/testing/plan-de-pruebas.md](docs/testing/plan-de-pruebas.md) | Plan de pruebas (testing kickoff) |
| [docs/testing/casos-de-prueba.xlsx](docs/testing/casos-de-prueba.xlsx) | Planilla de casos, suites smoke y regresión, resultados |
| [docs/testing/testing-exploratorio.md](docs/testing/testing-exploratorio.md) | Sesiones exploratorias y defectos |

## Ramas

`main` (entregas) · `dev` (desarrollo) · `test` (versión en prueba).

## Problemas conocidos

- **Docker Desktop no arranca** ("Virtual Machine Platform not enabled"): ejecutar
  `wsl.exe --install --no-distribution` en una terminal de administrador y reiniciar Windows.
  Mientras tanto, usar la opción C.
- **`Unable to establish loopback connection`** al arrancar un servicio: en algunos entornos de
  Windows fallan los sockets de dominio Unix que Java usa internamente. Agregar el argumento de
  JVM `-Djdk.net.unixdomain.tmpdir=Q:\no-existe` (una ruta que no exista) para que use TCP. Con el
  script: `.\scripts\start-local.ps1 -JvmArgs "-Djdk.net.unixdomain.tmpdir=Q:\no-existe"`.
- **El puerto 8080 está ocupado** en esta máquina, por eso el gateway usa el 8090 (`SERVER_PORT`
  o `GATEWAY_PORT` para cambiarlo).
- **Recién levantado**, el gateway puede responder 503 unos segundos: tarda hasta 30 s en conocer
  a los servicios que se registran en Eureka.
