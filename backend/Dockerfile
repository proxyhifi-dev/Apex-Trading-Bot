# ---- build stage ----
FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar -x test

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# Render provides PORT env var; Spring must use it
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
