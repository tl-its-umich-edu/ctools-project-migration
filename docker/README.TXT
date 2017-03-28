Build and run version of CPM war for local docker.

After initial configuration you can run CPM as simply as typing:
 build.sh
 docker run -p 8080:8080 -p 8090:8090 cpm_a

This has been tested with the OSX Docker install from
https://docs.docker.com/docker-for-mac/install/
It may work for other Docker installations.  Please document any differences for other
installations if you find some.

To use:
- make sure a local version of docker is running.
- go the the docker sub-directory and run build.sh.
- run the command the script prints to run CPM.

The first time only you'll need to do some configuration:
- Install an appropriate version of application.properties in the docker
directory.  A template is provided.
- Setup a user in tomcat-users.xml.  See the note on basic auth below.

The build script will:
- do a fresh build of CPM and include the Oracle db driver.
- install java and tomcat (and setup jpda remote debugging on port 8090).
- install basic auth into tomcat.
- install the cpm war as ROOT and copy in the local application.properties.

Notes:

- The Docker file, the tomcat files, and the application.properties file are kept in the docker
sub-directory as Docker doesn't use files from parent directories.
The build.sh script will copy in the required files and directories
when Docker builds the CPM image.

- Adding basic auth to tomcat makes it trivial to set REMOTE_USER for authentication.
You'll need to set up a specific username (uniqname) in local web.xml.
Likely the uniqname will need to be one in the Mcommunity CPM admin group.
The corresponding user password can be text you want except, of course,
your real password.  The default role of role1 should be sufficient for testing CPM.
If you need a more complicated user / role setup we can work that out.

- Basic auth doesn't have a concept of logging out.  It is simple to just restart the container to
test as a different user. If that isn't sufficent we can work on a different method.

- Docker images are meant to be immutible so picking up live changes to
files running in the tomcat will be akward.  The recommended approach would
to make changes to the regular source files and then rebuild and run
the new docker image.  For a project the size of CPM this is quite quick.
