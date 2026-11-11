#!/bin/bash
# Installs the Temurin JDK given as $1 (default 8). This project is
# strictly Java 8 — Management Center is pinned to 5.2.1, whose
# launcher only adds Java 9+ options when it detects such a JVM.
# Ubuntu 22.04 dropped openjdk-8 from its archives, and cloud-init
# package installs fail silently, so install synchronously here.
# apt reads the armored key directly — no gpg needed on the instance.

set -e

JAVA_MAJOR=${1:-8}

if java -version 2>&1 | grep -q "version \"${JAVA_MAJOR}\."; then
    java -version
    exit 0
fi

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo tee /etc/apt/keyrings/adoptium.asc >/dev/null
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb jammy main" \
    | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update -qq
sudo apt-get install -y -qq "temurin-${JAVA_MAJOR}-jdk"
java -version
