FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-gc:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/service-gc/build/libs/*.jar /app/app.jar
EXPOSE 8086
ENTRYPOINT ["java","-Xmx384m","-jar","/app/app.jar"]
