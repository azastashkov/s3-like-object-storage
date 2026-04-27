FROM eclipse-temurin:21-jre
COPY client-loadtest/build/install/client-loadtest /opt/loadtest
EXPOSE 8087
ENTRYPOINT ["/opt/loadtest/bin/client-loadtest"]
