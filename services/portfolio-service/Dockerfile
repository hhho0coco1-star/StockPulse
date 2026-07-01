FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY common ./common
ARG SERVICE=services/portfolio-service
COPY ${SERVICE} ./${SERVICE}
RUN gradle :services:portfolio-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/services/portfolio-service/build/libs/*-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8088
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
