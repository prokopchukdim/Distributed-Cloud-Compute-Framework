FROM openjdk:17
COPY target/worker-1.0-SNAPSHOT.jar worker-1.0-SNAPSHOT.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "/worker-1.0-SNAPSHOT.jar" ]