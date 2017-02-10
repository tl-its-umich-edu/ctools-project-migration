#!/usr/bin/env bash
### TTD:
#### - make prefix/instance a command line option.
#### - make sure that the packed script exists and is runnable.


# default to packed script
SCRIPT=./verifyAccessSiteMembership.pl.packed

# use plain perl version if it is more recent.
if [ -e "./verifyAccessSiteMembership.pl" ] && [ "./verifyAccessSiteMembership.pl" -nt $SCRIPT ]; then
   echo "USING MORE RECENT PERL SCRIPT";
   SCRIPT=./verifyAccessSiteMembership.pl
fi

function help {
    echo "$0: <site id file> {configuration file}"
    echo "Verify that site ids listed will respond to a CTools API request for membership."
    echo "It requires a credentials.yml file.  The name can be overridden on the command line."
    echo "The sql will be put in the file <site id file>.sql."
    }


CONFIG=${2:-credentials.yml}
SITEIDS=${1}

if [ $# -eq 0 ]; then
    help;
    echo $( $(pwd)/$SCRIPT -h);
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

echo "running: cat $SITEIDS | $SCRIPT $CONFIG >| $SITEIDS.membership"

cat $SITEIDS | $SCRIPT $CONFIG >| $SITEIDS.membership.csv

# make a file of the sql to run to fix the site membership.
perl -n -e '/sql:\s*(.+)\s*$/ && length($1) > 0 && print "$1\n"' $SITEIDS.membership.csv | sort -u >| $SITEIDS.membership.deleteunknown.sql
#end
