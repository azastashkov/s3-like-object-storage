FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-api:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-api/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx768m","-jar","/app/app.jar"]
