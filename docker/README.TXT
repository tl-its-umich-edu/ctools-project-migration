Build and run version of CPM war in local docker.

To use:
- make sure a local version of docker is running.
- go the the docker sub-directory and run build.sh.
- run the command the script prints to run CPM.
- make and customize a copy of application.properties in the docker directory.
A template version is provided.

The build script will:
- rebuild CPM with db driver.
- install java and tomcat (with jpda debugging on port 8090).
- install basic auth into tomcat.  You can set up test users and roles
in local web.xml and tomcat-users.xml
- install the cpm war as ROOT and copy in the local application.properties.

The Docker file, the tomcat files, and the application.properties file
are kept in the docker sub-directory for clarity.  The build.sh script
sets up the environment so that the right files and directories are
when Docker builds the CPM image.

