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
 #   local TIMESTAMP_value=$(ls artifact/ctools-project-migration.*.war | perl -n -e 'm/.+\.(.+)\.war/ && print $1' )
    local WEBAPPNAME_value=ctools-project-migration
    vars=`cat <<EOF
########################
# Environment variables for installation of this build.
WEBRELSRC=http://limpkin.dsc.umich.edu:6660/job/
JOBNAME=${JOB_NAME:-LOCAL}
BUILD=${BUILD_NUMBER:-imaginary}
ARTIFACT_DIRECTORY=artifact/artifact
#TIMESTAMP=${TIMESTAMP_value}
#VERSION=ctools-project-migration
WEBAPPNAME=${WEBAPPNAME_value}
WARFILENAME=ROOT
IMAGE_INSTALL_TYPE=war
echo "BUILD: \\${BUILD}"
echo "build:"
echo \\${BUILD}
echo "after build:"
IMAGE_NAME=${WEBAPPNAME_value}.\\${BUILD}.war
#######################
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
