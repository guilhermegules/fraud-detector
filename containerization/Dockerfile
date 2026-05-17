# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew jar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre

RUN apt-get update -qq && apt-get install -y -qq curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="\
-Xms32m \
-Xmx96m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=20 \
-XX:MaxRAMPercentage=75 \
-XX:+UseStringDeduplication \
-XX:ReservedCodeCacheSize=16m \
--add-modules=jdk.incubator.vector \
"

EXPOSE 8080

HEALTHCHECK --start-period=40s --interval=10s --timeout=5s --retries=10 \
  CMD curl -fs http://localhost:8080/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
