# This directory contains tools to help with the CPM migration.

There are two tools available.

    * verifySiteAccessMembership.sh - Verify that can get the
     sitemembership via the CTools direct api and create sql to fixup
     membership problems if they are found.
     * runRO.sh - generate SQL to make a site read-only.

# Obtaining the tools

Note: These scripts will not run on Windows. They will run on OSX and should
run on Linux.

Both tools are contained in a single tar file in the GitHub CPM repository
https://github.com/tl-its-umich-edu/ctools-project-migration.git They
are in the directory: src/sql/READ_ONLY.  The most recent build can be
downloaded from the TARS directory. The appropriate tar file starts with
CPMTools and also specifies a build date.

Let the tar file expand during download or double click to expand it.

Open a terminal shell and go the directory created when expanded.

# Input and output files

Both scripts expect input as a list of site ids, one per line.  Lines
that only have white space or that start with a comment (#) will be
ignored.  Output files will be generated in the same directory as the
input file.

Both scripts have configuration files to be explained later.

# Verifying sites have useable membership lists. #

Run the script as:
    ./verifyAccessSiteMembership.sh <site id file name>

There are two output files: <site id file name>.membership is a log of
the results of testing site membership.  The output contains 3
columns: the site id, the https status code, and a message.  For
successful requests the message will be "ok".  For unsuccessful
requests the status code will be returned and, if possible, there will
be sql that can be run later to fix the membership issue.  As a
convenience the sql will be collected into the file <site id file
name>.membership.deleteunknow.sql.  In production that sql will need
to be run by a DBA.  The generated sql should work in most cases. For
some situations the sql may not be correct (but it will be
harmless). Case by case solutions may be required in those cases.

The verifyAccessSiteMembership.sh script requires creating a
credentials.yml file containing the connection information for
connecting the the desired ctools instance.  To create this file copy
the credentials.yml.TEMPLATE file to credentials.yml, uncomment the
correct section for the ctools instance to be examined, and fill in
the appropriate user and password information. This script must access
a CTools instance directly and needs the credentials for a ctools
admin in that instance.

# Generating Read Only CTools site sql #

Run the script as:

    ./runRO.sh <task> <site id file name> {optional configuration file name}

The output sql will automatically be put in the file:

    <site id file name>.<task>.sql

Arguments:

-&lt;task>: Type of sql to generate.  The possible tasks are:
READ\_ONLY\_UPDATE, READ\_ONLY\_LIST, READ\_ONLY\_RESTORE, and
READ\_ONLY\_RESTORE\_LIST. The UPDATE tasks deal with removing
permissions.  The second two deal with restoring permissions from an
archive table.

-&lt;site id file name>: The input file of site ids can have any name. The
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

## Creating and running Read Only SQL.
1. Make sure that an copy of the *sakai\_realm\_rl\_fn* table has been
   made.  Since we expect little change in the sites it isn't
   necessary to make a copy every time the read_only scripts are run.
1. Update the corresponding yml file with the name of the archive
table.
1. Generate a file of site ids to update.
1. Run the script to generate sql and use the site ids file as input.
Use the ./runRO.sh wrapper script to run the tool.
1. Have a DBA run and commit the resulting sql.  In production it must
   be run by a DBA.


# Modifying and Releasing  the scripts ##

Developers should use the ./buildCPMTools.sh scripts to package up the
perl script into a 'packed' script that can be distributed. The build
will also package all the required files into a tar file for
distribution.  That file should be checked into the TARS directory and
then pushed to the git repository.

NOTE: Developers will need to have some CPAN packages installed to do
a build.  These will include FatPacker and YAML packages.  There may be
others as well.
