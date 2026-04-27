FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
COPY service-iam/build/libs/service-iam-*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java","-Xmx512m","-jar","/app/app.jar"]
