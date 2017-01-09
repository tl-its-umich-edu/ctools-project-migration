# This directory contains tools to help with the CPM migration.

There are two tools available.

    * runRO.sh - generate SQL to make a site read-only.
    * verifySiteAccessMembership.sh - Verify that can get the
     sitemembership via the CTools direct api and create sql to fixup
     membership problems if they are found.

# Obtaining the tools

Note: These scripts will not run on Windows. They will run on OSX and should
run on Linux.

Both tools are contained in a single tar file in the GitHub CPM repository
https://github.com/tl-its-umich-edu/ctools-project-migration.git They
are in the directory: src/sql/READ_ONLY.  The most released build can be
downloaded from the TARS directory.

Download the required tar file. The appropriate tar file starts with
CPMTools and also specifies a build date.

Let the tar file expand during download or double click to expand it.

Open a terminal shell and go the directory created when expanded.

# Input and output files

Both scripts expect input as a list of site ids, one per line.  Lines
that only have white space or that start with a comment (#) will be
ignored.  Output files will be generated in the same directory as the
input file.

# Verifying sites have useable membership lists. #

Run the script as:
    ./verifyAccessSiteMembership.sh <site id file name>

There are two output files.  <site id file name>.membership is a log
of the results of testing site membership. A line that contains only a
site id indicates that the site membership is ok for CPM.  All other
output lines are commented and indicate the membership status code and
site id.  If there is a problem retrieving membership due to an
unavailable user this line will also contain sql that can be used to
fix the problem.  As a convenience the file <site id file
name>.membership.deleteunknow.sql is created and contains all the
fixup sql generated in this run.  The user that runs the sql may need
to explicitly commit the change.

Note: The "membership" output file of the verify script can be used
directly as input to the runRO script.  Any lines that doesn't contain
a valid site id will be commented and therefore be ignored by the
runRO script.

The verifyAccessSiteMembership.sh script requires creating a
credentials.yml file containing the connection information for
connecting the the desired ctools instance.  To create this file copy
the credentials.yml.TEMPLATE file to credentials.yml, uncomment the
correct section for the ctools instance to be examined, and fill in
the appropriate user and password information. This script must access
a CTools instance directly and needs the credentials for a ctools
admin in the required instance.

# Generating Read Only CTools site sql #

Run the script as:

    ./runRO.sh <site id file name> {optional configuration file name}

The output sql will automatically be put in the file <site id file
name>.readonly.sql.

Arguments:

-<site id file name>: The input file of site ids can have any name. The
resulting sql files may be very large since the script generates many
sql statements for each site.  It may be good to break the input file
into multiple files of, say, 100 sites per file.

- {optional configuration file name}: The script takes an optional
second argument which names a yml configuration file. Suitable
configuration files are provided for CTools Prod, QA, and Dev. The
default file name in the script is for Prod so the normal user will
seldom need to specify a file name. For testing specify the
appropriate CTDEV or CTQA configuration file.  The name of the file
should make it easy to chose the correct one.  The configuration file
primarily specifies the roles and permissions to be modified.


## Modifying and Releasing  the scripts ##

Developers should use the ./buildCPMTools.sh scripts to package up the
perl script into a 'packed' script that can be distributed. The build
will also package all the required files into a tar file for
distribution.  That file should be checked into the TARS directory and
then pushed to the git repository.

NOTE: Developers will need to have some CPAN packages installed to do
a build.  These will include FatPacker and YAML packages.  There may be
others as well.
