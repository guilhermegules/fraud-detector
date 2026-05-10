# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY ../gradlew gradlew
COPY ../gradle gradle
COPY ../build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY ../src src
RUN ./gradlew bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="\
-Xms120m \
-Xmx120m \
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=1 \
-XX:+AlwaysPreTouch \
-XX:MaxRAMPercentage=75 \
-Xss2m \
-XX:-TieredCompilation \
-XX:CompileThreshold=1000 \
-XX:CICompilerCount=2 \
--add-modules=jdk.incubator.vector \
"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
