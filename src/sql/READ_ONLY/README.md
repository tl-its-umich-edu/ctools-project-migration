# This directory contains tools to help with the CPM migration.

# Obtaining the tools

The latest version of the code is available at: the GitHub CPM repository
https://github.com/tl-its-umich-edu/ctools-project-migration.git in
the directory: src/sql/READ_ONLY.  The latest released build can be
downloaded from the TARS directory.

Note: These scripts will not run on Windows. They will run on OSX and should
run on Linux.

Download the appropriate tar file.

Let the tar file expand during download or double click to expand it.

Open a terminal shell and go the directory created when expanded.

# Input files
The scripts expect input as a list of site ids, one per line.  Lines
that only have white space or that start with a comment (#) will be
ignored.

# Verifying sites have useable membership lists.
The appropriate tar file starts with verifyAccessSiteMembership and
contains a build date.

This script requires a credentials.yml file containing the connection
information for connecting the the desired ctools instance.  To create
this file copy the credentials.yml.TEMPLATE file to credentials.yml
and fill in the appropriate information.  The user should be a ctools
admin in that instance.  

Run the script as:
    ./runVerifyMember.sh <site id file name>

# Generating Read Only CTools site sql #

The tar file starts with ROSqlSite and contains a build date.

## Using the script ##

Run the script as:

    ./runRO.sh <site id file name> {configuration file name}

The output will automatically be put in the file <site id file name>.sql.

Arguments:

-<site id file name>: The input file of site ids can have any name
since the name is explicitly given to the runRO.sh script. The
resulting sql files may be very large since the script generates many
sql statements for each site.  It may be good to break the input file
into muliple files of, say, 100 sites per file.

- {configuration file name}: The script takes an optional second
argument which names a yml configuration file. Suitable configuration
files are provided for CTools Prod, QA, and Dev. The default file name
in the script is for Prod so the normal user will seldom need to
specify a file name. For testing specify the appropriate CTDEV or CTQA
configuration file.  The name of the file should make the use
obvious. The configuration file primarily specifies the roles and
permissions to be modified. The typical user will likely never need to
change a configuration file.

## Modifying and Releasing  the script ##

Developers should use the ./build.sh script to package up the perl
file into a script that can be distributed.  It will generate a tar
file that should be checked into the TARS directory and then pushed to
the git repository.

NOTE: Developers will need to have some CPAN packages installed to do
a build.  These will include FatPacker and YAML packages.  There may be
others.
