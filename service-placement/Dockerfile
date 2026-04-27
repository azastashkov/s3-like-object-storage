FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY service-placement/build/libs/service-placement-*.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
