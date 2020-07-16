#!/bin/bash
echo "Bygger spinnsyn-backend for bruk i flex-docker-compose"
./gradlew shadowJar
docker build -t spinnsyn-backend:latest .