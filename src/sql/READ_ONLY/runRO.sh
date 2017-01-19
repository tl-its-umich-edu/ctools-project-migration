#!/usr/bin/env bash
### TTD:
#### - make prefix/instance a command line option.
#### - make sure that the packed script exists and is runnable.

# default to packed script but use pl version if more recent.
PREFIX="./generateROSqlSite.pl"
PACKED=${PREFIX}.packed
SCRIPT=$PACKED
# use plain perl version if it is more recent.
if [ -e "${PREFIX}" ] && [ "${PREFIX}" -nt "$PACKED" ]; then
   echo "USING MORE RECENT PERL SCRIPT";
   SCRIPT=$PREFIX;
fi

function help {
    echo "$0: <site id file> {configuration file}"
    echo "Generate CTools site read-only sql.  Requires a file of site ids (one per line)"
    echo "and an optional configuration file name."
    echo "The sql will be put in the file <site id file>.sql."
    }


CONFIG=${2:-ROSql-20161206-PROD.yml}
SITEIDS=${1}

if [ $# -eq 0 ]; then
    help
    exit 1
fi


if [ ! -e "${SITEIDS}" ]; then
    echo "ERROR: must provide file of siteIds."
    exit 1;
fi

if [ ! -e "${CONFIG}" ]; then
    echo "ERROR: config file ${CONFIG} does not exist.";
    exit 1;
fi

echo "running: cat $SITEIDS | ${SCRIPT} $CONFIG >| $SITEIDS.sql"

cat ${SITEIDS} | ${SCRIPT} ${CONFIG} >| ${SITEIDS}.readonly.sql
#end
