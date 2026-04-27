FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-placement:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-placement/build/libs/*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
