#FROM openjdk:17
FROM docker:24.0.6-dind-alpine3.18

RUN apk update && \
    apk add openjdk17-jre-headless java-cacerts && \
    update-ca-certificates -f
# RUN apk add openrc docker-openrc --no-cache
# RUN mkdir -p /run/openrc/exclusive && mkdir -p /run/openrc && touch /run/openrc/softlevel

COPY target/worker-1.0-SNAPSHOT.jar worker-1.0-SNAPSHOT.jar
EXPOSE 8080

USER root

ENTRYPOINT ["java", "-jar", "/worker-1.0-SNAPSHOT.jar" ]
# "service", "docker", "start"