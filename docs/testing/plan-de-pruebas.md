# Plan de pruebas — Digital Money House

Testing kickoff del proyecto. Define cómo se prueba, cómo se escribe un caso, cómo se reporta un
defecto y con qué criterio un caso entra en la suite de humo o en la de regresión.

## 1. Alcance

**Sprint 1 — dentro del alcance**

| Funcionalidad | Endpoints |
|---|---|
| Registro de usuario con cuenta digital (CVU y alias) | `POST /users/register` |
| Login | `POST /auth/login` |
| Logout | `POST /user/logout` |
| Verificación de email con código de 6 dígitos (opcional) | `POST /auth/verify-email`, `POST /auth/verify-email/resend` |
| Recupero de contraseña por mail (opcional) | `POST /auth/forgot-password`, `POST /auth/reset-password`, `GET /auth/reset-password.html` |
| Seguridad transversal | validación de token en el gateway, permisos por dueño/rol, endpoints internos |
| Infraestructura | registro en Eureka, enrutamiento del gateway, Swagger |

**Fuera del alcance en este sprint:** frontend, pruebas de carga y performance, pruebas sobre
infraestructura productiva, y las funcionalidades de los sprints 2 a 4 (tarjetas, actividad,
transferencias).

## 2. Niveles y tipos de prueba

| Nivel | Qué verifica | Con qué | Dónde está |
|---|---|---|---|
| Unitarias | Una clase aislada (reglas de negocio, generadores, filtros) | JUnit 5 + Mockito + AssertJ | `*/src/test/java/**/service`, `**/security` |
| Integración por servicio | Un microservicio completo (HTTP → seguridad → JPA) contra H2, con los otros servicios simulados | Spring Boot Test + MockMvc | `*ApiTest.java` |
| Sistema (end-to-end) | Los 5 microservicios juntos, entrando por el gateway | Casos manuales, ejecutados con `scripts/run_test_suite.py` | `docs/testing/casos-de-prueba.xlsx` |
| Exploratorias | Lo que los casos escritos no anticipan | Sesiones con charter | `docs/testing/testing-exploratorio.md` |

Los tests unitarios y de integración se ejecutan con `./mvnw verify` y deben estar en verde antes
de cada commit a `dev`. A partir del Sprint 2 la suite de humo se automatiza con Java y RestAssured.

## 3. Entornos

| Entorno | Base de datos | Mails | Uso |
|---|---|---|---|
| Tests (`./mvnw verify`) | H2 en memoria | mock | unitarias e integración |
| Local sin Docker (`scripts/start-local.ps1`, perfil `h2`) | H2 en memoria | `scripts/fake_smtp.py` | sistema y exploratorias cuando no hay Docker |
| Local con Docker (`docker compose up`) | MySQL 8.4 | MailHog | sistema y exploratorias (entorno de referencia) |

## 4. Cómo escribir un caso de prueba

Un caso de prueba verifica **una sola cosa** y lo puede ejecutar alguien que no conoce el sistema.
Cada caso es una fila de la planilla con estos campos:

| Campo | Qué poner |
|---|---|
| **ID** | `TC-NNN`, único y estable. No se reutiliza aunque el caso se elimine. |
| **Historia / épica** | La historia de usuario que valida. |
| **Título** | El comportamiento que se verifica, no la acción: "Login con contraseña incorrecta", no "Probar login". |
| **Precondiciones** | Estado necesario antes de empezar (datos, sesión, servicios). Si no hay, "—". |
| **Pasos** | Acciones concretas y numerables, con los datos exactos a ingresar. |
| **Resultado esperado** | Observable y verificable: código HTTP, campos, mensajes. Nunca "funciona bien". |
| **Tipo** | Positivo, Negativo, Borde o Seguridad. |
| **Prioridad** | Alta, Media o Baja (ver abajo). |
| **Suite** | "Smoke + Regresión" o "Regresión". |
| **Test automatizado** | El test JUnit que cubre lo mismo, si existe (trazabilidad). |
| **Estado / Resultado obtenido / Fecha** | Se completan en cada ejecución. |

Reglas:

1. **Independiente**: no depende de que otro caso se haya ejecutado antes. Cada uno crea sus propios datos.
2. **Repetible**: se puede ejecutar muchas veces con el mismo resultado (por eso los datos de
   prueba usan emails y DNI únicos).
3. **Un solo motivo para fallar**: si el caso verifica dos cosas no relacionadas, se divide en dos.
4. **Por cada camino feliz, sus caminos de error**: por cada criterio de aceptación se escribe al
   menos un caso positivo, y uno negativo por cada respuesta de error que la historia enumera
   (400, 404, 500...).
5. **Valores límite**: cuando hay una restricción (largo mínimo 8, 22 dígitos), se prueba el
   límite válido y el primer valor inválido.

Prioridad:

- **Alta**: si falla, el usuario no puede completar la historia, o hay un riesgo de seguridad o de
  pérdida de datos.
- **Media**: hay una alternativa para el usuario, o es un caso de borde poco frecuente.
- **Baja**: cosmético o de muy baja probabilidad.

Ejemplo:

> **TC-033 — Login con contraseña incorrecta** · HU2 Login · Negativo · Alta · Regresión
> **Precondiciones:** usuario registrado con email verificado.
> **Pasos:** `POST /auth/login` con el email correcto y otra contraseña.
> **Resultado esperado:** HTTP 400 (contraseña incorrecta). No se entrega token.

## 5. Cómo reportar un defecto

Un defecto se reporta como *issue* en el repositorio (etiqueta `bug`). Un buen reporte permite
reproducirlo sin preguntarle nada a quien lo encontró:

| Campo | Contenido |
|---|---|
| **ID y título** | `DEF-NNN` + qué pasa y dónde: "El registro acepta emails sin dominio (`a@b`)". |
| **Entorno** | Rama y commit, perfil (h2 / Docker), sistema operativo. |
| **Severidad** | Impacto técnico (ver abajo). |
| **Prioridad** | Urgencia para corregirlo, la define el equipo. |
| **Precondiciones** | Estado previo necesario. |
| **Pasos para reproducir** | Numerados, con los datos exactos (el `curl` o el JSON enviado). |
| **Resultado obtenido** | Lo que pasó: código HTTP y cuerpo de la respuesta. |
| **Resultado esperado** | Lo que debería pasar y por qué (criterio de aceptación o regla que lo respalda). |
| **Evidencia** | Respuesta completa, log del servicio, captura. |
| **Caso de prueba relacionado** | El `TC-NNN` que lo detectó, o "exploratorio — sesión N". |

Severidad:

- **Crítica**: sistema caído, pérdida o exposición de datos, falla de seguridad explotable. Bloquea la entrega.
- **Alta**: una historia no se puede completar y no hay alternativa.
- **Media**: comportamiento incorrecto con alternativa, o validación faltante.
- **Baja**: mensajes, formato, detalles que no afectan el uso.

Ciclo de vida: **Nuevo → Confirmado → En curso → Corregido → Verificado → Cerrado** (o *Rechazado*
si no es un defecto). Quien lo verifica vuelve a ejecutar los pasos sobre la versión corregida, y
todo defecto corregido deja un caso en la suite de regresión (y, si aplica, un test JUnit) para que
no vuelva a aparecer.

## 6. Criterio para incluir un caso en la suite de humo (smoke)

La suite de humo responde una sola pregunta: **¿este build está lo bastante sano como para
seguir probándolo?** Se ejecuta completa después de cada despliegue y tiene que terminar en pocos
minutos. Un caso entra si cumple **todas** estas condiciones:

1. Verifica el **camino feliz de una funcionalidad crítica** (sin ella el producto no se puede
   usar): registrarse, verificar el email, iniciar sesión, usar el token, cerrar sesión.
2. O es una verificación **mínima e indispensable de seguridad o infraestructura**: un recurso
   protegido rechaza peticiones sin token, el token invalidado deja de servir, los servicios están
   registrados y el entorno levanta.
3. Es **rápido** y **estable**: no depende de esperas largas, de datos externos ni de apagar servicios.
4. Si falla, **no tiene sentido seguir**: el build se rechaza.

No entran en smoke los casos negativos de validación, los de borde ni las combinaciones de datos.

## 7. Criterio para incluir un caso en la suite de regresión

La suite de regresión responde: **¿lo que ya funcionaba sigue funcionando?** Se ejecuta completa
antes de cada entrega y cuando se toca código compartido (seguridad, manejo de errores, gateway).
Un caso entra si cumple **alguna** de estas condiciones:

1. Está en la suite de humo (regresión incluye a smoke).
2. Verifica un **criterio de aceptación** de una historia ya entregada, positivo o negativo.
3. Cubre una **regla de negocio o de seguridad**: unicidad de email y DNI, formato de CVU y alias,
   permisos por dueño y rol, bloqueo por intentos, links de un solo uso.
4. Reproduce un **defecto que ya se corrigió**: todo defecto cerrado deja su caso.
5. Cubre una zona que **cambia seguido** o de la que dependen otras.

Un caso **sale** de regresión solo cuando la funcionalidad se elimina o cuando otro caso ya cubre
exactamente lo mismo. Los casos que queden desactualizados por un cambio de requisitos se
actualizan (mantenimiento de la planilla), no se borran.

## 8. Criterios de entrada y salida del sprint

**Entrada** (para empezar a probar): el código compila, `./mvnw verify` está en verde y el entorno levanta.

**Salida** (para dar el sprint por probado):

- 100 % de la suite de humo ejecutada y aprobada.
- 100 % de la suite de regresión ejecutada; los casos no aprobados tienen un defecto asociado o
  figuran como bloqueados con su motivo.
- Ningún defecto abierto de severidad crítica o alta.
- Los defectos corregidos fueron verificados y tienen su caso de regresión.

## 9. Entregables de testing del Sprint 1

| Entregable | Archivo |
|---|---|
| Plan de pruebas | `docs/testing/plan-de-pruebas.md` (este documento) |
| Planilla de casos de prueba, con clasificación smoke/regresión y resultado de la ejecución | `docs/testing/casos-de-prueba.xlsx` |
| Resultado crudo de la última ejecución | `docs/testing/resultados-ejecucion.json` |
| Notas del testing exploratorio y defectos encontrados | `docs/testing/testing-exploratorio.md` |
| Scripts de ejecución | `scripts/run_test_suite.py`, `scripts/build_test_sheet.py` |
