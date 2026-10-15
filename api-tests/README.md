# api-tests — Tests automatizados de la API (RestAssured)

Framework de automatización de los casos de prueba. Los tests se ejecutan contra el sistema
**levantado**, entrando por el API Gateway, igual que un cliente real. Por eso no corren en el build
normal (`mvnw verify` los saltea).

## Requisitos

El sistema levantado:

- con Docker: `docker compose up -d --build`, o
- sin Docker: `.\scripts\start-local.ps1`.

## Cómo ejecutarlos

```bash
# Todos (suite de regresión automatizada), con Docker: los mails se leen de MailHog
./mvnw -pl api-tests test -DskipApiTests=false

# Solo la suite de humo
./mvnw -pl api-tests test -DskipApiTests=false -Dgroups=smoke

# Sin Docker: los mails los guarda scripts/fake_smtp.py en mails.jsonl
./mvnw -pl api-tests test -DskipApiTests=false -Dmail.source=file:C:/ruta/al/proyecto/mails.jsonl
```

Propiedades configurables:

| Propiedad | Valor por defecto | Para qué |
|---|---|---|
| `api.baseUrl` | `http://localhost:8090` | Dirección del API Gateway |
| `api.eurekaUrl` | `http://localhost:8761` | Para verificar el registro de servicios |
| `mail.source` | `mailhog:http://localhost:8025` | De dónde leer los mails (código de verificación, link de recupero) |

El resultado queda en `api-tests/target/surefire-reports`.

## Organización

| Clase | Qué cubre |
|---|---|
| `AuthApiTest` | Sprint 1: registro, verificación de email, login, logout, Eureka |
| `AccountApiTest` | Sprint 2: dinero disponible, últimos movimientos, alias |
| `UserProfileApiTest` | Sprint 2: ver y editar el perfil |
| `CardApiTest` | Sprint 2: alta, listado, detalle y baja de tarjetas |
| `support/ApiTest` | Base: configuración de RestAssured y creación de usuarios de prueba |
| `support/Mailbox` | Lectura de los mails desde MailHog o desde `mails.jsonl` |

Cada test se identifica con el ID del caso de la planilla (`@DisplayName("TC-121 ...")`) y lleva
etiquetas de JUnit: todos tienen `regression` y los de la suite de humo, además, `smoke`.

Cada test crea sus propios usuarios con email y DNI únicos, así que se pueden ejecutar en cualquier
orden y repetir sobre la misma base.
