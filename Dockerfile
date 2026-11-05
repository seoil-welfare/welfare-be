# 운영 서버용 이미지. 사용법은 deploy/README.md
# 1단계: jar 빌드 (테스트는 CI·로컬에서 돌리고 여기서는 건너뛴다)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
# Windows에서 받은 gradlew는 줄바꿈이 CRLF일 수 있어 리눅스에서 실행되게 고친다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# 2단계: 실행 (JRE만, root가 아닌 사용자로)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/build/libs/*.jar app.jar
USER app
# 이 이미지는 항상 운영 설정으로 뜬다. local 프로필의 가상 데이터(db/dev)가 운영 DB에 들어가지 않게 한다
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
