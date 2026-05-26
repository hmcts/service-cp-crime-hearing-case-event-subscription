# Simple shell script to build spring boot jarfile and then docker image
set -ex
cd ..
projectname=$(echo $PWD | sed 's/^.*\///')

echo "Building projectname $projectname"
./gradlew clean bootjar

echo "Building docker $projectname"
docker build -t $projectname .

export DOCKER_IMAGE=$projectname
