FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY service-gc/build/libs/service-gc-*.jar /app/app.jar
EXPOSE 8086
ENTRYPOINT ["java","-Xmx384m","-jar","/app/app.jar"]
