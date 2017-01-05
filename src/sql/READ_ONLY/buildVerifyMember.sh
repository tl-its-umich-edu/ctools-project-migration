#!/usr/bin/env bash
# package a perl script to generate sql to make sites read-only.
#set -x

# LIMITATION: This script will only work if the fatpack has been installed here.
SITEBIN="/opt/local/libexec/perl5.22/sitebin";
APP=${1:-verifyAccessSiteMembership.pl}
APP_OUT=$APP.packed

# temporary build directory
BUILD_DIR=TMP_BUILD.DIR
TAR_DIR=$(pwd)/TARS

function niceTimestamp {
    echo $(date +"%F-%H-%M")
}

function build_tar {
    TS=$(niceTimestamp)

    # make sure there is a place to put built tars.
    [ -e ${TAR_DIR} ] || mkdir ${TAR_DIR}

    # create a clean temporary build directory.
    [ -e ${BUILD_DIR} ] && rm -rf ${BUILD_DIR}
    mkdir ${BUILD_DIR}

    cp runVerifyMember.sh ${BUILD_DIR}
    cp credentials.yml.TEMPLATE ${BUILD_DIR}
    cp README.md ${BUILD_DIR}
    chmod +x *packed *.sh
    cp verifyAccessSiteMembership.pl.packed ${BUILD_DIR}
    TAR_OUT=${TAR_DIR}/verifyAccessSiteMembership.${TS}.tar
    tar -cf ${TAR_OUT} -C ${BUILD_DIR} .
    echo "+++ Created ${TAR_OUT} for verify site membership tool."
    rm -rf ${BUILD_DIR}
}

function pack_script {
    ## pack up the script and support files.
    PATH=$PATH:$SITEBIN fatpack pack  ./$APP >| ${APP_OUT}
    return_value=$?
    if [ $return_value != 0 ]; then
        echo "$0: failed to generate ${APP_OUT}"
        exit -1
    fi
    echo "+++ Created ${APP_OUT} with sql generation tool." 
}

echo "+++ Generate portable perl script for $APP"
echo "+++ Please ignore error messages about 'pod' files."

pack_script

build_tar

#end
