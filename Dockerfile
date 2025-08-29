FROM eclipse-temurin:17-jre-alpine

# installa curl e netcat e dos2unix
RUN apk add --no-cache curl netcat-openbsd dos2unix

# crea un utente non-root
RUN addgroup -S spring && adduser -S spring -G spring

# crea la directory per gli upload e imposta i permessi
RUN mkdir -p /tmp/uploads && chown -R spring:spring /tmp/uploads

WORKDIR /app

COPY target/cloud-services-business-monitoring-1.0.jar app.jar
COPY keycloak/wait-for-keycloak.sh /app/wait-for-keycloak.sh

RUN dos2unix /app/wait-for-keycloak.sh && \
    chmod +x /app/wait-for-keycloak.sh && \
    chown spring:spring /app/wait-for-keycloak.sh

# imposta l'utente non-root
USER spring:spring

EXPOSE 8090

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# avvia l'applicazione con le opzioni JVM configurate
ENTRYPOINT ["/app/wait-for-keycloak.sh"]