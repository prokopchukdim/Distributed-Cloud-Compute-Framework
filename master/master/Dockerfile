FROM openjdk:17
COPY target/master-1.0-SNAPSHOT.jar master-1.0-SNAPSHOT.jar
COPY DemoResources DemoResources/
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "/master-1.0-SNAPSHOT.jar" ]