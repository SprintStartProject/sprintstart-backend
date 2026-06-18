#FROM eclipse-temurin:21
#RUN addgroup --system spring && adduser --system --ingroup spring spring
#USER spring:spring
#ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
#COPY ${JAR_FILE} app.jar
#ENTRYPOINT ["java","-jar","/app.jar"]

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew .
RUN sed -i 's/\r$//' gradlew

COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring

RUN mkdir -p /app/uploads /tmp/repos \
    && chown -R spring:spring /app/uploads /tmp/repos

COPY --from=build /app/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]