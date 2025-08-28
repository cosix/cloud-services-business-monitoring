#!/bin/sh
set -e

echo "Waiting for Keycloak to be ready..."

sleep 90

# attendi che Keycloak sia in ascolto sulla porta
# until nc -z keycloak 8080; do
#  echo "Keycloak is unavailable - sleeping"
#  sleep 5
#done

# echo "Keycloak is up - checking if it is ready to accept requests"

# attendi che Keycloak risponda correttamente alle richieste
# until curl -s http://keycloak:8080/realms/master > /dev/null; do
#  echo "Keycloak is not ready yet - sleeping"
#  sleep 5
# done

# echo "Keycloak is ready - starting application"
echo "Starting application"
exec java $JAVA_OPTS -jar app.jar