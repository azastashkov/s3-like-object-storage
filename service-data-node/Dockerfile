FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-data-node:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-data-node/build/libs/*.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
