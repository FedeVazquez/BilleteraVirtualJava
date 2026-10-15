# Testing exploratorio — Sprint 4

**Fecha:** 15/10/2026 · **Build:** rama `dev` · **Entorno:** local sin Docker (perfil `h2`), los 5
microservicios levantados y todas las peticiones entrando por el API Gateway.

**Alcance:** lo agregado en el sprint: transferencias a CVU/CBU/alias
(`POST /accounts/{id}/transferences`), últimos destinatarios (`GET /accounts/{id}/transferences`) y
el comprobante en PDF (`GET /accounts/{id}/activity/{transactionId}/receipt`). También se probó la
clave de idempotencia (`Idempotency-Key`), que se agregó en este sprint a partir de la observación
O-6 del Sprint 3.

## Organización

| Sesión | Charter | Tours | Pruebas |
|---|---|---|---|
| 1 | Explorar las **transferencias** con destinos mal escritos, saldos al límite, envíos repetidos y claves de idempotencia, para descubrir formas de perder o duplicar dinero. | Tour de los datos, tour del impaciente (doble clic, reintentos), tour del saboteador | 22 |
| 2 | Explorar el **comprobante y los destinatarios** revisando el PDF generado y la lista en distintos escenarios, para descubrir datos incorrectos o accesos indebidos. | Tour del documento (revisión visual del PDF), tour de la puerta trasera | 9 |

## Sesión 1 — Transferencias

**Notas**

- Alias con espacios alrededor y en mayúsculas → se reconoce (200).
- **CVU pegado con espacios internos** (`8686183366 744402401522`) → 400 "La cuenta destino no existe". Es habitual copiar el CVU con espacios. → DEF-010.
- CVU de 21 dígitos → 400. CBU de un banco externo (22 dígitos que no son de DMH) → 400 "cuenta inexistente", como pide la consigna. → O-10.
- Destino vacío o ausente → 400 con mensaje claro.
- Descripción de 101 caracteres → 400; sin descripción → se guarda "Transferencia"; con HTML → se guarda como texto (el frontend debe escaparlo). → O-11.
- Transferir **todo** el saldo → 200 y saldo 0.00; una transferencia más de $0,01 → 410 "Fondos insuficientes: el saldo disponible es $0.00".
- Si el destinatario cambia su alias, la lista de destinatarios muestra el alias nuevo; transferir al alias viejo → 400.
- **Doble clic sin clave de idempotencia**: dos transferencias de $60 con saldo $100 al mismo tiempo → una 200 y otra 410; saldo final $40. El bloqueo de la cuenta impide gastar el mismo dinero dos veces.
- **Cinco envíos simultáneos con la misma `Idempotency-Key`** → los cinco responden 200 con la misma transferencia y el dinero se mueve una sola vez.
- **Misma clave con otro monto** ($1 en lugar de $60) → 200 con la transferencia de $60 original. El cliente cree que hizo una transferencia de $1 que nunca ocurrió. → DEF-009.
- Clave de 65 caracteres → 400. La misma clave en otra cuenta → es independiente (las claves son por cuenta).
- Fondos insuficientes → 410 con el saldo disponible en el mensaje.

## Sesión 2 — Comprobante y destinatarios

**Notas**

- Se descargaron y revisaron visualmente tres comprobantes: transferencia enviada, transferencia recibida e ingreso desde tarjeta. Muestran monto con formato argentino (`$ 1.234,56`), signo (+/−), número de operación, fecha y hora de Argentina, tipo, descripción, origen y destino con titular, CVU y alias, o la tarjeta. Coinciden con el detalle de la actividad.
- El PDF se descarga con `Content-Disposition: attachment; filename="comprobante-{id}.pdf"`.
- Comprobante sin token → 401; de un movimiento inexistente → 404; de otra cuenta → 404 o 403 según la cuenta de la URL.
- Pedir el comprobante con `Accept: application/pdf` cuando hay un error devolvía 406 en lugar del error real, porque el JSON de error no se puede servir como PDF. Se detectó al escribir el test automatizado y se corrigió antes de esta sesión: los errores siempre salen en JSON.
- Cuatro transferencias al mismo destinatario → aparece una sola vez, con el monto de la última.
- Recibir una transferencia no agrega al remitente a la lista de destinatarios (solo cuentan las enviadas).

## Defectos

| ID | Severidad | Descripción | Cómo se reproduce | Estado |
|---|---|---|---|---|
| DEF-009 | Alta | Reutilizar una clave de idempotencia con otros datos devuelve la operación original como si fuera la nueva. | Transferir $60 con `Idempotency-Key: K`; luego $1 con la misma clave → 200 con la transferencia de $60. Esperado: 409. | **Corregido y verificado** (TC-183): se guarda una huella de los datos de la operación junto con la clave. |
| DEF-010 | Baja | Un CVU con espacios o guiones no se reconoce. | Transferir a `86861833 6674440-240152` → 400. | **Corregido y verificado** (TC-184): se quitan espacios y guiones cuando el destino es numérico. |

## Observaciones

| ID | Observación | Recomendación |
|---|---|---|
| O-10 | Solo se puede transferir a cuentas de Digital Money House: un CBU de un banco real responde "cuenta inexistente". Es lo que pide la consigna. | Para transferencias a otros bancos haría falta integrar una red de pagos (fuera del alcance). |
| O-11 | La descripción de la transferencia acepta cualquier texto, incluido HTML. | El frontend debe mostrarla escapada. Evaluar limitar caracteres. |
| O-12 | El nombre del titular se obtiene de users-service con las cuentas bloqueadas. Si users-service tarda, la transferencia espera (tiempo máximo configurado: 2 s de conexión y 3 s de lectura) y se completa sin el nombre. | Obtener los nombres antes de bloquear las cuentas, o guardarlos en account-service. |

## Cierre

Dos sesiones, 31 pruebas, 2 defectos (1 alto, 1 bajo), ambos corregidos y verificados con tests
automatizados y casos de regresión. Ninguna prueba logró mover dinero sin saldo, gastar dos veces el
mismo dinero ni operar sobre una cuenta ajena.
