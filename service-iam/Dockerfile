FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-iam:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-iam/build/libs/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
