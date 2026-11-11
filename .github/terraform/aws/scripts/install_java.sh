#!/bin/bash
# Installs Temurin 8 JDK (Hazelcast 5.2 supports Java 8).
# Ubuntu 22.04 dropped openjdk-8 from its archives, and cloud-init
# package installs fail silently, so install synchronously here.
# apt reads the armored key directly — no gpg needed on the instance.

set -e

if command -v java >/dev/null 2>&1; then
    java -version
    exit 0
fi

sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo tee /etc/apt/keyrings/adoptium.asc >/dev/null
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb jammy main" \
    | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update -qq
sudo apt-get install -y -qq temurin-8-jdk
java -version
