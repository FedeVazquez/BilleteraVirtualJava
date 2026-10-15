# QA Sign-off — Digital Money House (sprints 1 a 4)

**Fecha:** 15/10/2026 · **Versión:** rama `dev` (commit del cierre del Sprint 4) · **Responsable de QA:** equipo del proyecto

## Resultado

**Apto para entregar.** Se ejecutaron todas las suites; los 138 casos pasaron y no quedan defectos
abiertos.

## Métricas que pide la consigna

| Métrica | Valor |
|---|---|
| **Casos de prueba ejecutados** | **138** de 138 (planilla `casos-de-prueba.xlsx`) |
| **Casos de prueba pasados** | **138** de 138: 136 en el entorno local (perfil H2) y los 2 del entorno Docker con MySQL (TC-086 y TC-087) en GitHub Actions: ver "Entorno Docker" |
| **Defectos reportados** | **10** |
| **Defectos resueltos** | **10** (0 abiertos) |
| **Casos automatizados** | **75** con RestAssured (contra el sistema levantado) · además **190** tests JUnit de unidad e integración · **41** requests con scripts en Postman |

## Detalle por sprint

| Sprint | Casos | Pasaron | Automatizados (RestAssured) | Defectos encontrados |
|---|---|---|---|---|
| 1 — Registro, login, logout, opcionales | 72 | 72 (2 en Docker, GitHub Actions) | 12 | 5 (DEF-001 a DEF-005) |
| 2 — Cuenta, perfil y tarjetas | 28 | 28 | 28 | 0 |
| 3 — Actividad e ingreso de dinero | 21 | 21 | 19 | 3 (DEF-006 a DEF-008) |
| 4 — Transferencias, destinatarios y comprobante | 17 | 17 | 16 | 2 (DEF-009 y DEF-010) |
| **Total** | **138** | **138** | **75** | **10** |

Suite de humo: 22 casos (21 automatizados). Suite de regresión: los 138.

## Defectos

| ID | Sprint | Severidad | Descripción | Estado |
|---|---|---|---|---|
| DEF-001 | 1 | Media | El registro aceptaba emails sin dominio completo (`a@b`) | Resuelto |
| DEF-002 | 1 | Baja | El DNI enviado como número se convertía a texto en silencio | Resuelto |
| DEF-003 | 1 | Media | Nombre y apellido aceptaban HTML | Resuelto |
| DEF-004 | 1 | Alta | Cambiar la contraseña no cerraba las sesiones abiertas | Resuelto |
| DEF-005 | 1 | Baja | El gateway respondía 500 sin formato con un servicio caído | Resuelto |
| DEF-006 | 3 | Media | Monto y tarjeta aceptados como texto en el ingreso de dinero | Resuelto |
| DEF-007 | 3 | Baja | Los errores de conversión exponían clases internas de Java | Resuelto |
| DEF-008 | 3 | Baja | El filtro de tipo de movimiento distinguía mayúsculas | Resuelto |
| DEF-009 | 4 | Alta | Reutilizar la clave de idempotencia con otros datos devolvía la operación original | Resuelto |
| DEF-010 | 4 | Baja | Un CVU con espacios o guiones no se reconocía | Resuelto |

Por severidad: 2 altas, 3 medias, 5 bajas. Todos los defectos resueltos tienen un test JUnit y un
caso en la suite de regresión que los cubre. El detalle está en los documentos de testing
exploratorio de cada sprint.

## Entorno Docker

La PC de desarrollo no puede ejecutar Docker. Por eso el entorno con Docker Compose (MySQL, MailHog y
los 5 servicios) se prueba en **GitHub Actions** en cada push: el workflow `CI` construye las
imágenes, levanta el entorno, corre la automatización RestAssured completa y la suite de casos contra
él, y verifica la persistencia en MySQL reiniciando los servicios (TC-087). El resultado de cada
ejecución queda en la pestaña *Actions* del repositorio, con los reportes como artefactos.

**Resultado:** ejecución CI #4 (commit `08442ee`) en verde: imágenes construidas, entorno levantado,
RestAssured 75 de 75 contra MySQL y MailHog, suite de casos aprobada y persistencia verificada después
de reiniciar users-service, account-service y auth-service. Las ejecuciones #1 a #3 fallaron por errores
de los scripts de prueba del propio pipeline (guardar el resultado y esperar a los servicios después del
reinicio), no del sistema; se corrigieron en `c23c04e` y `08442ee`.

## Riesgos y observaciones abiertas

No bloquean la entrega; quedan para conversar con el negocio o para una próxima versión:

- El login distingue "usuario inexistente" (404) de "contraseña incorrecta" (400), como pide la consigna: permite averiguar qué emails están registrados (O-2).
- No hay límite de intentos de login (O-3) ni límites de monto diario (O-9).
- La actividad no está paginada (O-8).
- Solo se puede transferir a cuentas de Digital Money House (O-10).
- El despliegue en una cuenta real de AWS no se probó (requiere una cuenta propia).

## Criterios de salida

| Criterio (plan de pruebas) | Cumplido |
|---|---|
| 100 % de la suite de humo ejecutada y aprobada | Sí |
| 100 % de la regresión ejecutada; lo no aprobado con defecto asociado o bloqueado con motivo | Sí |
| Ningún defecto abierto de severidad crítica o alta | Sí (0 abiertos) |
| Defectos corregidos verificados y con caso de regresión | Sí |
