# Imagen unica para los 5 microservicios: el argumento MODULE elige cual se empaqueta.
#   docker build --build-arg MODULE=users-service -t dmh/users-service .
# (docker-compose.yml ya lo hace para cada servicio)

# ---------- Etapa 1: compilar con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
# El repositorio de Maven queda en una cache de Docker: las dependencias se descargan una sola vez.
# Los tests se corren fuera de Docker (./mvnw verify); aca solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package

# ---------- Etapa 2: imagen final, solo con el JRE y el jar ----------
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
# El proceso no corre como root dentro del contenedor.
RUN useradd --system --no-create-home dmh
COPY --from=build /workspace/${MODULE}/target/${MODULE}.jar app.jar
USER dmh
ENTRYPOINT ["java", "-jar", "app.jar"]
