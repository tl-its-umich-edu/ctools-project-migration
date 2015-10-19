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

## relocate the war file
mv target/ctools-project-migration*.war ctools-project-migration.war

## clean the target folder
rm -rf target

## construct new tar file
find . -maxdepth 2 -type f \( -name "*_version.txt" -o -name "ctools-project-migration.war" \) -exec tar -rf ./artifact/CPM_$build_number_$gitNum.tar {} \;

## remove war file
rm ctools-project-migration.war
