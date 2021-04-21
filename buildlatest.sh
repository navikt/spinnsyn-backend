#!/bin/bash
echo "Bygger spinnsyn-backend for bruk i flex-docker-compose"
rm -rf ./build
./gradlew bootJar
docker build -t spinnsyn-backend:latest .
