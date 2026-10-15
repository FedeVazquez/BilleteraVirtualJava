# Despliegue en AWS

Sprint 4 — Infraestructura: *"Generar archivo Docker Compose"* y *"Generar imagen de container Docker
para deployar en la nube (AWS)"*.

## Piezas

| Archivo | Para qué |
|---|---|
| `Dockerfile` | Una imagen por microservicio (argumento `MODULE`). Compila en una etapa con Maven y deja solo el JRE y el `.jar` en la imagen final, con un usuario sin privilegios. |
| `docker-compose.yml` | Entorno local completo: construye las imágenes y levanta MySQL, MailHog y los 5 servicios. |
| `.github/workflows/ci.yml` | En cada push: compila, corre los tests y prueba el entorno de Docker de punta a punta. |
| `.github/workflows/publish-images.yml` | Construye las 5 imágenes y las publica en GitHub Container Registry y, si se configura, en Amazon ECR. |
| `docker-compose.aws.yml` | Levanta el sistema en un servidor usando las imágenes publicadas (no compila nada). |

## 1. Publicar las imágenes

En GitHub: **Actions → Publicar imágenes → Run workflow** (o crear un tag `v1.0.0`). Se publican:

```
ghcr.io/fedevazquez/dmh-eureka-server
ghcr.io/fedevazquez/dmh-api-gateway
ghcr.io/fedevazquez/dmh-auth-service
ghcr.io/fedevazquez/dmh-users-service
ghcr.io/fedevazquez/dmh-account-service
```

con la etiqueta `latest` y la del tag o la rama.

Los paquetes de GHCR nacen privados: para descargarlos desde AWS sin credenciales, en GitHub ir a
**Packages → cada paquete → Package settings → Change visibility → Public**, o hacer
`docker login ghcr.io` en el servidor con un token de acceso personal con permiso `read:packages`.

### Opcional: publicar también en Amazon ECR

1. En ECR, crear los 5 repositorios: `dmh-eureka-server`, `dmh-api-gateway`, `dmh-auth-service`, `dmh-users-service`, `dmh-account-service`.
2. En IAM, configurar GitHub como proveedor OIDC (`token.actions.githubusercontent.com`) y crear un rol con permiso para subir a esos repositorios (política `AmazonEC2ContainerRegistryPowerUser`), que solo pueda asumir este repositorio.
3. En el repositorio de GitHub, **Settings → Secrets and variables → Actions**:
   - variable `AWS_REGION` (por ejemplo `us-east-1`),
   - variable `AWS_ECR_REGISTRY` (`<cuenta>.dkr.ecr.<region>.amazonaws.com`),
   - secreto `AWS_ROLE_ARN` (el ARN del rol).

Con OIDC no se guardan claves de acceso de AWS en GitHub: el workflow pide credenciales temporales en cada ejecución.

## 2. Opción A — Un servidor EC2 con Docker Compose (la más simple)

Adecuada para una demo o para la entrega del proyecto.

1. Crear una instancia EC2 Amazon Linux 2023 o Ubuntu, de tipo `t3.medium` (4 GB de RAM: son 6 JVM más MySQL).
2. Grupo de seguridad: entrada solo por el puerto 443/80 desde un balanceador, o el 8090 desde tu IP para probar. SSH (22) solo desde tu IP. **No abrir** 3306, 8761 ni los puertos de los servicios.
3. Instalar Docker y el plugin de Compose. En Amazon Linux:
   ```bash
   sudo dnf install -y docker && sudo systemctl enable --now docker && sudo usermod -aG docker ec2-user
   ```
4. Copiar al servidor `docker-compose.aws.yml`, `docker/mysql/init.sql` y un `.env` hecho a partir de `.env.example` con **valores propios** para `JWT_SECRET`, `INTERNAL_API_KEY`, `CARD_FINGERPRINT_KEY`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `PUBLIC_URL` y los datos del SMTP.
5. Levantar:
   ```bash
   docker compose -f docker-compose.aws.yml --env-file .env up -d
   docker compose -f docker-compose.aws.yml ps
   ```
6. Delante, un **Application Load Balancer** con certificado de ACM (HTTPS) que reenvíe al puerto 8090.

Para el correo, **Amazon SES** con credenciales SMTP (`MAIL_HOST=email-smtp.<region>.amazonaws.com`,
puerto 587, `MAIL_USERNAME` y `MAIL_PASSWORD`). Para la base, en lugar del contenedor MySQL se puede
usar **Amazon RDS for MySQL**: definir `DB_HOST` con su endpoint y crear las tres bases con
`docker/mysql/init.sql`.

## 3. Opción B — ECS Fargate + RDS (la del diseño de infraestructura)

Es la arquitectura de `docs/infraestructura.md`, con alta disponibilidad:

| Componente del diseño | Servicio de AWS |
|---|---|
| Balanceador HTTPS + WAF | Application Load Balancer + AWS WAF + certificado de ACM |
| Servicios en subred privada, 2 zonas | ECS Fargate: un *service* por microservicio, 2 tareas cada uno |
| Descubrimiento de servicios | Eureka como servicio de ECS, o ECS Service Connect (Cloud Map) |
| MySQL primario y réplica | Amazon RDS for MySQL, Multi-AZ |
| Correo | Amazon SES |
| Secretos | AWS Secrets Manager (inyectados como variables de entorno en la definición de tarea) |
| Logs y métricas | CloudWatch Logs y CloudWatch Metrics |
| Imágenes | Amazon ECR (publicadas por el workflow) |

Cada definición de tarea usa la imagen de ECR del servicio y las mismas variables de entorno que
`docker-compose.aws.yml`. Solo el *target group* del gateway se conecta al balanceador.

## Lo que se verificó y lo que no

- `docker compose config` valida los dos archivos de Compose.
- El workflow **CI** construye las imágenes con el `Dockerfile`, levanta `docker-compose.yml`
  completo en un runner de GitHub, corre contra él la automatización RestAssured y la suite de casos,
  y verifica que los datos persistan en MySQL tras reiniciar los servicios.
- El despliegue en una cuenta real de AWS (EC2 o ECS) **no se ejecutó**: requiere una cuenta y
  credenciales propias.
