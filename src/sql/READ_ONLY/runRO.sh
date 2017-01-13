#!/usr/bin/env bash
### TTD:
#### - make prefix/instance a command line option.
#### - make sure that the packed script exists and is runnable.

function help {
    echo "$0: <site id file> {configuration file}"
    echo "Generate CTools site read-only sql.  Requires a file of site ids (one per line)"
    echo "and an optional configuration file name."
    echo "The sql will be put in the file <site id file>.sql."
    }


#set -x
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

echo "running: cat $SITEIDS | ./generateROSqlSite.pl.packed $CONFIG >| $SITEIDS.sql"

cat $SITEIDS | ./generateROSqlSite.pl.packed $CONFIG >| $SITEIDS.readonly.sql
#end
