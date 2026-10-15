# Digital Money House — Backend

Billetera virtual. Desafío profesional de la especialización Backend, con arquitectura de
microservicios. **Sprint 1:** registro con cuenta digital (CVU y alias), login, logout y, como
opcionales, verificación de email y recupero de contraseña. **Sprint 2:** dinero disponible, perfil y
tarjetas. **Sprint 3:** actividad con filtros e ingreso de dinero desde tarjeta. **Sprint 4:**
transferencias a CVU/CBU/alias, últimos destinatarios y comprobante en PDF.

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
account-service/   Cuenta digital, tarjetas, movimientos, transferencias y comprobantes (:8083)
docker/            Script de inicialización de MySQL
scripts/           Entorno local sin Docker y ejecución de los casos de prueba
docs/              Arquitectura, ejemplos de la API, Postman, testing e informes
```

## Arquitectura

![Arquitectura y red del entorno](docs/img/arquitectura-local.svg)

- El **cliente** solo habla con el **api-gateway** (puerto 8090). El gateway valida el token contra
  `auth-service` y enruta cada request al servicio que corresponde, buscándolo en **Eureka** (`lb://`).
- Cada servicio tiene **su propia base** en MySQL; ninguno lee las tablas de otro. Cuando necesitan
  datos de otro servicio se llaman por HTTP con **OpenFeign**: `auth-service` pide credenciales a
  `users-service`, `users-service` le pide a `account-service` que cree la cuenta al registrarse y
  `account-service` le pide a `users-service` el nombre del titular para las transferencias.
- Los mails (código de verificación y recupero de contraseña) salen por SMTP; en desarrollo los
  atrapa **MailHog**.

Más detalle (red, puertos, propuesta para producción) en [docs/infraestructura.md](docs/infraestructura.md).

## Requisitos

- **Java 21** (JDK). Maven no hace falta: el proyecto trae el wrapper (`mvnw` / `mvnw.cmd`).
- **Docker** con Docker Compose (opciones A y B). Sin Docker se puede usar la opción C.
- Git, y opcionalmente Python 3 (scripts de prueba) y Postman.

## Cómo levantarlo

```bash
git clone https://github.com/FedeVazquez/BilleteraVirtualJava.git
cd BilleteraVirtualJava
```

### Opción A — Todo con Docker

```bash
docker compose up -d --build
```

La API queda en `http://localhost:8090`, los mails en `http://localhost:8025` (MailHog) y el panel
de Eureka en `http://localhost:8761`. Para cambiar secretos o puertos, copiar `.env.example` a `.env`.

### Opción B — Desde Eclipse, con la infraestructura en Docker

1. `docker compose up -d mysql mailhog`
2. En Eclipse: **File → Import → Maven → Existing Maven Projects**, elegir esta carpeta y marcar
   los proyectos. El JRE del workspace tiene que ser Java 21.
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

### Comprobar que está andando

Los servicios tardan alrededor de un minuto en arrancar y registrarse en Eureka. Está listo cuando
`http://localhost:8761` muestra los 4 servicios (`API-GATEWAY`, `AUTH-SERVICE`, `USERS-SERVICE`,
`ACCOUNT-SERVICE`) y este login de prueba responde **404** (usuario inexistente):

```bash
curl -i -X POST http://localhost:8090/auth/login -H "Content-Type: application/json" -d '{"email":"nadie@mail.com","password":"Clave1234"}'
```

## Probar el sistema desde cero

Recorrido completo de la billetera, en orden (todo contra el gateway, `http://localhost:8090`):

1. **Registrarse**: `POST /users/register`. La respuesta trae el `id` del usuario, el `accountId`,
   el CVU y el alias.
2. **Buscar el código** de 6 dígitos en MailHog (`http://localhost:8025`) o, sin Docker, en `mails.jsonl`.
3. **Verificar el email**: `POST /auth/verify-email` con el email y el código.
4. **Login**: `POST /auth/login`. Copiar el `token` y mandarlo en el resto de los requests como
   `Authorization: Bearer <token>`.
5. **Ver el saldo**: `GET /accounts/{accountId}` (arranca en 0).
6. **Asociar una tarjeta**: `POST /accounts/{accountId}/cards` (por ejemplo `4509953566233704`).
7. **Ingresar dinero**: `POST /accounts/{accountId}/transferences` con `cardId` y `amount`.
8. **Transferir**: registrar y verificar un segundo usuario, y transferirle con
   `POST /accounts/{accountId}/transferences` usando su alias o CVU en `destination`.
9. **Ver la actividad** (`GET /accounts/{accountId}/activity`) y **descargar el comprobante**
   (`GET /accounts/{accountId}/activity/{id}/receipt`).
10. **Logout**: `POST /user/logout`. Después de eso el token deja de servir (401).

Tres formas de hacerlo sin escribir los requests a mano:

- **Postman**: importar [la colección y el entorno](docs/postman) y ejecutar la colección completa
  (*Run collection*). Los scripts guardan el token, los ids y el código de verificación solos
  (el código lo leen de MailHog, así que necesita el entorno con Docker).
- **Swagger UI**: `http://localhost:8090/swagger-ui.html`.
- **Tests automáticos** contra el sistema levantado: ver [Tests](#tests).

Cada paso con su request y su response real está en **[docs/api-ejemplos.md](docs/api-ejemplos.md)**.

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
| `POST /accounts/{id}/transferences` | Bearer | Ingresar dinero desde una tarjeta propia: `{"cardId": 1, "amount": 1500}`. | 201 · 400 · 403 · 404 |
| `POST /accounts/{id}/transferences` | Bearer | Transferir a otra cuenta: `{"destination": "CVU, CBU o alias", "amount": 1500, "description": "opcional"}`. Header opcional `Idempotency-Key` para no repetir la operación. | 200 · 400 cuenta inexistente · 403 · 409 · 410 fondos insuficientes |
| `GET /accounts/{id}/transferences` | Bearer | Últimas 5 cuentas a las que se transfirió. | 200 · 403 |
| `GET /accounts/{id}/activity` | Bearer | Toda la actividad, de la más reciente a la más antigua. Filtros opcionales: `direction` (INCOME/EXPENSE), `from`, `to` (AAAA-MM-DD), `minAmount`, `maxAmount`. | 200 · 400 · 403 |
| `GET /accounts/{id}/activity/{transactionId}` | Bearer | Detalle de un movimiento. | 200 · 400 · 403 · 404 |
| `GET /accounts/{id}/activity/{transactionId}/receipt` | Bearer | Comprobante del movimiento en PDF. | 200 · 403 · 404 |

Todos los errores tienen el mismo formato:

```json
{ "timestamp": "2026-10-02T19:52:47Z", "status": 400, "error": "Bad Request",
  "message": "Datos de entrada inválidos", "path": "/users/register",
  "details": ["email: El email no tiene un formato válido"] }
```

### Ejemplos

Registro:

```bash
curl -X POST http://localhost:8090/users/register -H "Content-Type: application/json" \
  -d '{"firstName":"Juan","lastName":"Perez","dni":"30123456","email":"juan.perez@mail.com","phone":"+5491122334455","password":"Clave1234"}'
```

```json
201 Created
{ "id": 1, "firstName": "Juan", "lastName": "Perez", "dni": "30123456", "email": "juan.perez@mail.com",
  "phone": "+5491122334455", "accountId": 1, "cvu": "2923847932147848876593", "alias": "fogata.ciervo.jilguero" }
```

Login (después de verificar el email):

```bash
curl -X POST http://localhost:8090/auth/login -H "Content-Type: application/json" \
  -d '{"email":"juan.perez@mail.com","password":"Clave1234"}'
```

```json
200 OK
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 3600 }
```

Transferencia a otra cuenta por alias:

```bash
curl -X POST http://localhost:8090/accounts/1/transferences -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: pago-cena-001" \
  -d '{"destination":"marea.rio.diente","amount":1500.50,"description":"Cena del viernes"}'
```

```json
200 OK
{ "id": 2, "accountId": 1, "type": "TRANSFER_OUT", "direction": "EXPENSE", "amount": 1500.50,
  "description": "Cena del viernes", "date": "2026-10-15T16:51:54.941354Z",
  "counterpart": { "accountId": 2, "cvu": "5382383311879707114523", "alias": "marea.rio.diente", "name": "Ana Gomez" } }
```

Sin saldo suficiente:

```json
410 Gone
{ "timestamp": "2026-10-15T16:51:55.096Z", "status": 410, "error": "Gone",
  "message": "Fondos insuficientes: el saldo disponible es $48499.50", "path": "/accounts/1/transferences" }
```

Todos los endpoints, con request y response reales: **[docs/api-ejemplos.md](docs/api-ejemplos.md)**.

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
| [docs/api-ejemplos.md](docs/api-ejemplos.md) | Request y response reales de cada endpoint, en el orden de uso |
| [docs/informe-sprint-1.pdf](docs/informe-sprint-1.pdf) | Qué se hizo, y cuándo y para qué se usó cada herramienta |
| [docs/infraestructura.md](docs/infraestructura.md) | Diseño de la infraestructura, red y componentes |
| [docs/testing/plan-de-pruebas.md](docs/testing/plan-de-pruebas.md) | Plan de pruebas (testing kickoff) |
| [docs/testing/casos-de-prueba.xlsx](docs/testing/casos-de-prueba.xlsx) | Planilla de casos, suites smoke y regresión, resultados |
| [docs/testing/testing-exploratorio.md](docs/testing/testing-exploratorio.md) | Testing exploratorio del Sprint 1 |
| [docs/testing/testing-exploratorio-sprint-3.md](docs/testing/testing-exploratorio-sprint-3.md) | Testing exploratorio del Sprint 3 |
| [docs/testing/testing-exploratorio-sprint-4.md](docs/testing/testing-exploratorio-sprint-4.md) | Testing exploratorio del Sprint 4 |
| [docs/testing/qa-sign-off.md](docs/testing/qa-sign-off.md) | QA sign-off: casos ejecutados, aprobados, defectos y automatización |
| [api-tests/README.md](api-tests/README.md) | Automatización con RestAssured |
| [docs/postman](docs/postman) | Colección de Postman con scripts de prueba (importar colección y entorno) |
| [docs/deploy-aws.md](docs/deploy-aws.md) | Imágenes Docker y despliegue en AWS |
| [docs/informe-sprints-2-a-4.pdf](docs/informe-sprints-2-a-4.pdf) | Informe de trabajo de los sprints 2 a 4 |

## Integración continua

En cada push a `main`, `dev` o `test`, GitHub Actions (`.github/workflows/ci.yml`) compila, corre los
tests y además levanta el entorno completo con Docker Compose para correr la automatización y la
suite de casos contra él. Las imágenes para AWS se publican a pedido con el workflow
"Publicar imágenes" (ver `docs/deploy-aws.md`).

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
- **"La ejecución de scripts está deshabilitada"** al correr los `.ps1` con Windows PowerShell:
  ejecutarlos con `powershell -ExecutionPolicy Bypass -File .\scripts\start-local.ps1`, o habilitar
  scripts locales una vez con `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.
- **El puerto 8080 está ocupado** en esta máquina, por eso el gateway usa el 8090 (`SERVER_PORT`
  o `GATEWAY_PORT` para cambiarlo).
- **Recién levantado**, el gateway puede responder 503 unos segundos: tarda hasta 30 s en conocer
  a los servicios que se registran en Eureka.
