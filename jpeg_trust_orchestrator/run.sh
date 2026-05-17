#!/bin/bash

set -e

echo "--- Compilando mipams-jpeg-systems ---"
cd mipams-jpeg-systems/
mvn clean install -DskipTests
cd ..

echo "--- Compilando mipams-jpeg-trust ---"
cd mipams-jpeg-trust/
mvn clean install -DskipTests
cd ..

echo "--- Iniciando aplicación Spring Boot ---
./mvnw spring-boot:run