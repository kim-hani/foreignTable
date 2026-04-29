# 1. Base Image: 애플리케이션 구동을 위한 최적화된 경량 JDK 환경 세팅
FROM eclipse-temurin:17-jdk-alpine

# 2. Working Directory: 컨테이너 내 프로세스 실행 및 파일 관리 기준 경로 지정
WORKDIR /app

# 3. Build Argument: 호스트(로컬/CI)에서 빌드된 최종 아티팩트(.jar) 경로 변수화
ARG JAR_FILE=build/libs/*.jar

# 4. Time Setting : 컨테이너 내부 시간대를 한국 표준시(KST)로 설정하여 로그 및 시간 관련 기능 정상화
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

# 5. Copy Artifact: 빌드된 .jar 파일을 컨테이너 내부의 표준 이름(app.jar)으로 복사
COPY ${JAR_FILE} app.jar

# 6. Entry Point: 컨테이너 실행 시 Spring Boot 애플리케이션을 가동하는 메인 프로세스 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]