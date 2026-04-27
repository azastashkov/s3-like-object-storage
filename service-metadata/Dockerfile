FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-metadata:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY --from=build /src/service-metadata/build/libs/*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
