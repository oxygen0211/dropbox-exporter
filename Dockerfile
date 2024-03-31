FROM docker.io/maven:3.9-amazoncorretto-21 as build

WORKDIR /tmp/src
COPY . .

RUN mvn package

FROM amazoncorretto:21
COPY --from=build /tmp/src/target/dropbox-exporter-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar", "--spring.config.location=file:///app/config"]