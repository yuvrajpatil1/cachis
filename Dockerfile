FROM eclipse-temurin:23-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY pom.xml .
COPY src src

RUN mvn -q -B package -DskipTests -Ddir=/tmp/codecrafters-build-redis-java

FROM eclipse-temurin:23-jre-alpine
WORKDIR /app

COPY --from=build /tmp/codecrafters-build-redis-java/codecrafters-redis.jar .

ENTRYPOINT [ "java", "-jar", "codecrafters-redis.jar" ]