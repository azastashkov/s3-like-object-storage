FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-data-routing:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-data-routing/build/libs/*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
