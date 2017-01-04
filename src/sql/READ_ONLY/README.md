# Generating Read Only CTools site sql #

This directory contains code to generate SQL to turn CTools sites read-only.

The latest version of the code is available at: the GitHub CPM repository
https://github.com/tl-its-umich-edu/ctools-project-migration.git in
the directory: src/sql/TLCPM-511.  The latest released build can be
downloaded from the TARS directory.

Note: This script will not run on Windows.  It will run on OSX and should
run on Linux.

## Using the script ##

Download the tar file.

Let the tar file expand during download or double click to expand it.

Open a terminal shell and go the directory created when expanded.

Create input file(s) with one site id per line.  The file can have blank
lines and comment lines starting with a #.

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
