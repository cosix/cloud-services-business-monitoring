#!/bin/sh
set -e

echo "Waiting for Keycloak to be ready..."

sleep 90

echo "Starting application"
exec java $JAVA_OPTS -jar app.jar