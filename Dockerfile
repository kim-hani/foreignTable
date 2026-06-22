# Stage 1: Gradle로 JAR 빌드
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar -x test

# Stage 2: 빌드된 JAR로 실행 이미지 생성
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

COPY --from=builder /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
