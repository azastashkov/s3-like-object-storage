FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY service-data-node/build/libs/service-data-node-*.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java","-Xmx1g","-jar","/app/app.jar"]
