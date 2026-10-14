FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress test package \
    org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/classes ./classes
COPY --from=build /build/target/dependency ./lib
USER 10001:10001
EXPOSE 8787
# OpenJDK 17 reads HTTP limits at JVM startup; requests must not pin workers indefinitely.
# The 55-second response budget leaves room for the provider's 45-second timeout.
# Render's command override is documented inconsistently; a complete CMD is unambiguous.
ENTRYPOINT []
CMD ["java", "-Xmx256m", \
    "-Dsun.net.httpserver.maxReqTime=15", "-Dsun.net.httpserver.maxRspTime=55", \
    "-Dsun.net.httpserver.timerMillis=1000", \
    "-Dsun.net.httpserver.maxReqHeaders=32", "-Dsun.net.httpserver.maxReqHeaderSize=16384", \
    "-Djdk.httpserver.maxConnections=40", "-Dsun.net.httpserver.maxIdleConnections=8", \
    "-Dsun.net.httpserver.idleInterval=15", "-Dsun.net.httpserver.clockTick=1000", \
    "-cp", "/app/classes:/app/lib/*", "com.runstate.reflection.ReflectionBackendMain", "--remote"]
