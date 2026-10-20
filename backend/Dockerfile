FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S sentinel && adduser -S -D -H -G sentinel -u 10001 sentinel
WORKDIR /app
COPY --from=build /workspace/target/api-sentinel-1.0.0.jar /app/api-sentinel.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Dfile.encoding=UTF-8"
ENTRYPOINT ["java", "-jar", "/app/api-sentinel.jar"]
