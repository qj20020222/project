#!/bin/bash

app_env=${1:-development}

# Development environment commands
dev_commands() {
    echo "Running development environment commands..."
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    ./mvnw clean spring-boot:run
}

# Production environment commands
prod_commands() {
    echo "Running production environment commands..."
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
    export PATH=$JAVA_HOME/bin:$PATH
    ./mvnw clean install
    ./mvnw spring-boot:run
}

# prod_commands
# Check environment variables to determine the running environment
if [ "$app_env" = "production" ] || [ "$app_env" = "prod" ] ; then
    echo "Production environment detected"
    prod_commands
else
    echo "Development environment detected"
    dev_commands
fi
