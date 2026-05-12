# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="\
-Xms90m \
-Xmx120m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=1 \
-XX:+AlwaysPreTouch \
-XX:-TieredCompilation \
-XX:CompileThreshold=1000 \
-XX:MaxRAM=160m \
-XX:ReservedCodeCacheSize=64m \
--add-modules=jdk.incubator.vector \
"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
