# Dockerfile (project root)
FROM eclipse-temurin:21

WORKDIR /app

COPY build/libs/*.jar /app/

EXPOSE ${SERVER_PORT:-8082}

ENTRYPOINT ["sh","-c","exec java -jar $(ls /app/*.jar | grep -v 'plain' | head -n1)"]
