#!/bin/sh --

## find the next Jenkins build number from environment setting: BUILD_NUMBER
## if missing, set the number to be 1

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

## relocate the war file
mv target/ctools-project-migration*.war artifact/ctools-project-migration.${build_number}.war

function writeEnvironmentVariables {
    # Values to be used while writing lines into the here document need to be evaluated here.
    # Values written by the here document can be used when the Makefile itself is run.
    # Variables values to be used when writting the script must be defined outside of the
    # here document.  Values explicitly defined in the here document can only be used
    # at build time, hence the escaping of the $ values.
    local WEBAPPNAME_value=ctools-project-migration
    local BUILD_value=${BUILD_NUMBER:-imaginary}
    local DATE_value=$(date --iso-8601=seconds)
    vars=`cat <<EOF
########################
# Created at ${DATE_value}.
# Environment variables for installation of this CPM build.
##### STATIC VALUES #######
WEBRELSRC=http://limpkin.dsc.umich.edu:6660/job
ARTIFACT_DIRECTORY=artifact/artifact
IMAGE_INSTALL_TYPE=war
WARFILENAME=ROOT
##### VALUES SET AT BUILD TIME. ########
JOBNAME=${JOB_NAME:-LOCAL}
WEBAPPNAME=${WEBAPPNAME_value}
VERSION=${WEBAPPNAME_value}
BUILD=${BUILD_NUMBER:-imaginary}
##### VALUES SET AT INSTALL TIME. #######
IMAGE_NAME=\\\${WEBAPPNAME}.\\\${BUILD}.war
CONFIGURATION_NAME=configuration-files.\\\${BUILD}.tar
ARTIFACTFILE=\\\${WEBRELSRC}/\\\${JOBNAME}/\\\${BUILD}/\\\${ARTIFACT_DIRECTORY}/\\\${IMAGE_NAME}
CONFIGFILE=\\\${WEBRELSRC}/\\\${JOBNAME}/\\\${BUILD}/\\\${ARTIFACT_DIRECTORY}/\\\${CONFIGURATION_NAME}
#######################
EOF`
    echo "${vars}"
}

writeEnvironmentVariables >| VERSION.Makefile
#end

