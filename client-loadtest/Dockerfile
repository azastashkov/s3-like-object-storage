FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :client-loadtest:installDist -x test --no-daemon

FROM eclipse-temurin:21-jre
COPY --from=build /src/client-loadtest/build/install/client-loadtest /opt/loadtest
EXPOSE 8087
ENTRYPOINT ["/opt/loadtest/bin/client-loadtest"]
