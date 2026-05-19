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

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="\
-Xms64m \
-Xmx112m \
-XX:+UseG1GC \
-XX:MaxRAMPercentage=75 \
-XX:ReservedCodeCacheSize=16m \
--add-modules=jdk.incubator.vector \
"

EXPOSE 8080

HEALTHCHECK --start-period=40s --interval=10s --timeout=5s --retries=10 \
  CMD curl -fs http://localhost:8080/ready || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
