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
mv target/ctools-project-migration*.war artifact/ctools-project-migration.${build_number}.war

function writeEnvironmentVariables {
    # Values to be used while writing lines into the here document need to be evaluated here.
    # Values written by the here document can be used when the Makefile itself is run.
    # Variables values to be used when writting the script must be defined outside of the
    # here document.  Values explicitly defined in the here document can only be used
    # at build time, hence the escaping of the $ values.
    local WEBAPPNAME_value=ctools-project-migration
    local BUILD_value=${BUILD_NUMBER:-imaginary}
    local DATE_VALUE=$(date --iso-8601=seconds)
    vars=`cat <<EOF
########################
# Environment variables for installation of this build at ${DATE_value}.
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
ARTIFACTFILE=\\\${WEBRELSRC}/\\\${JOBNAME}/\\\${BUILD}/\\\${ARTIFACT_DIRECTORY}/\\\${IMAGE_NAME}
#######################
EOF`
    echo "${vars}"
}

writeEnvironmentVariables >| VERSION_2.Makefile


# timestamp is TIMESTAMP=${TIMESTAMP_value} is not in the current one
# ####################################
# # identify Jenkins project directory and type of install
# + WEBRELSRC=http://limpkin.dsc.umich.edu:6660/job/
# + ARTIFACT_DIRECTORY=artifact/artifact
# + IMAGE_INSTALL_TYPE=war

# # project specification
# JOBNAME=CTools_Project_Migration (DIFFERS)

# # artifact specification
# +WEBAPPNAME=ctools-project-migration
# # for compatibility
# +VERSION=${WEBAPPNAME}
# # name of the installed war file.  Could be application name or ROOT or whatever.
# # will have '.war' appended to it.
# + WARFILENAME=ROOT
# # specify which build to pick up
# BUILD=186 (DIFFERS)
# # unique identifier supplied by build and placed in war and tar file names
# UID=${BUILD}
# ########## no need to change these.
# # name of war file built by Jenkins
# IMAGE_NAME=${WEBAPPNAME}.${UID}.war

# ARTIFACTFILE=${WEBRELSRC}/${JOBNAME}/${BUILD}/${ARTIFACT_DIRECTORY}/${IMAGE_NAME}
# #CONFIGFILE=${WEBRELSRC}/${JOBNAME}/${BUILD}/${ARTIFACT_DIRECTORY}/${CONFIGURATION_NAME}
