#!/bin/sh --

## find the next Jenkins build number from environment setting: BUILD_NUMBER
## if missing, set the number to be 1
echo "shell env variable BUILD_NUMBER is ${BUILD_NUMBER}"
build_number=${BUILD_NUMBER:=1}

## check the artifact folder, whether it exists or not
if [ ! -d "artifact" ]; then
    ## no artifact folder
    ## create such folder first
    echo "create artifact folder"
    mkdir artifact
    chmod -R 755 artifact
fi
echo "Generating zip file for build number: ${build_number}"
## get git hex number
gitNum=`git log -n 1 --pretty="format:%h"`
printf 'github version number is %s.' $gitNum > git_version.txt
rm target/*
mvn package
mv target/ctools-project-migration*.war target/ctools-project-migration.war
find . -maxdepth 1 -type f \( -name "*_version.txt" -o -name "target/ctools-project-migration.war" \) -exec tar -rf ./artifact/CPM_$build_number_$gitNum.tar {} \;
