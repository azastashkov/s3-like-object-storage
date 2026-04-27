FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY service-data-routing/build/libs/service-data-routing-*.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java","-Xmx1g","-jar","/app/app.jar"]
