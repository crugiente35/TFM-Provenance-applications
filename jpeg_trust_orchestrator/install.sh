#!/bin/bash
set -e

# 1. Actualizar el sistema e instalar dependencias necesarias
echo "--- Updating the system and installing dependencies ---"
sudo apt update
sudo apt install -y default-jdk maven git

# 2. Compilar mipams-jpeg-systems
echo "--- Compiling and installing mipams-jpeg-systems ---"
cd mipams-jpeg-systems/
mvn clean install -DskipTests
cd ..
# 3. Compilar mipams-jpeg-trust
echo "--- Compiling mipams-jpeg-trust ---"
cd mipams-jpeg-trust/
mvn clean install -DskipTests
cd ..

echo "--- ¡Process completed succesfully! ---"
