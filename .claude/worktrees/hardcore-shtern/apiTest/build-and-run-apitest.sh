# Simple shell script to run the api test
# We call build-and-build-and-run-docker.sh to do its job
# And then we call docker-compose up and wait for the app to be up
set -ex
cd ..
projectname=$(echo $PWD | sed 's/^.*\///')
cd apiTest

echo "STARTED project ${projectname} at $(date)"

./build-docker.sh

echo "Running docker compose up --wait ... (waits for all healthchecks to pass, timeout 10 min)"
export DOCKER_IMAGE=$projectname

# --wait blocks until all services with healthchecks are healthy (or fails on timeout).
# Ordering enforced by depends_on condition:service_healthy in docker-compose.yml:
#   sqledge -> servicebus (90s start_period + port 5300 check) -> app1/app2 (actuator/health)
docker compose up -d --wait --wait-timeout 600

echo "Running ./gradlew test"
./gradlew test
docker compose down

echo "ENDED at $(date)"
