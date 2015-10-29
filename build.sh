#!/bin/sh --

## find the next Jenkins build number from environment setting: BUILD_NUMBER
## if missing, set the number to be 1
echo "shell env variable BUILD_NUMBER is ${BUILD_NUMBER}"
build_number=${BUILD_NUMBER:=1}

## check the artifact folder, whether it exists or not
## remove the artificat folder first
if [ -d "artifact" ]; then
    rm -rf artifact
fi
## add the artifact folder back
if [ ! -d "artifact" ]; then
    ## no artifact folder
    ## create such folder first
    echo "create artifact folder"
    mkdir artifact
    chmod -R 755 artifact
fi
echo "For build number: ${build_number}"

## relocate the war file
<<<<<<< HEAD
mv target/ctools-project-migration*.war artifact/ctools-project-migration.war
=======
mv target/ctools-project-migration*.war ctools-project-migration.${build_number}.war

## clean the target folder
rm -rf target
>>>>>>> upstream/master
