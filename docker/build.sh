#!/usr/bin/env bash
# Build a Docker image.  All required files must be in the current directory (with the docker file).
# TTD:
### for variations:
###  - could setup one docker file and load different scripts with variable settings.
###  - use ARG in docker file and pass in values on build with --build-arg=<something>
### specify what to copy down
### have docker dir with Dockerfile that pulls values from local/load/dev/qa/prod subdirectories?
### having settings file so can determine which applications.properties to use? "settings.sh" ? source it in here.
### copy down when run this build.
### make clear what files are required / used.
### move java/tomcat versions to settings file? or parent docker?
### make local port setable.  (Not always 8080)
### docker in jenkins?
### be clear about basic auth and how not to include it
### multiple docker files with base that is extended for different versions (e.g. basic auth)?
### :-) can use local built image in FROM stmt

#set -x
set -e
DOCKER_TAG=cpm_a
PROFILES=" -P db-driver-oracle "

WEB_PORT=" -p 8080:8080 "
JPDA_PORT=" -p 8090:8090 "

# build the war file
(cd ..;
 echo "mvn clean package ${PROFILES}"
 mvn clean package ${PROFILES}
)
# copy it down
cp ../target/*war .

docker build -t ${DOCKER_TAG} .

echo -e "+++ run with: \n#docker run ${WEB_PORT} ${JPDA_PORT} ${DOCKER_TAG}"
#end
