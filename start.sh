#!/bin/bash -x

# copy app/tomcat config files from secret volumes to a location that can be written to.
if [ -e /mnt/app ];
then
    cp /mnt/app/* /usr/local/tomcat/home/.
fi

if [ -e /mnt/tomcat ];
then
    cp /mnt/tomcat/* /usr/local/tomcat/conf/.
fi

# Oracle JDBC driver should be in persistent volume
# Copy to $CATALINA_HOME/lib at run-time
if [ -e /mnt/oracle ];
then
    cp /mnt/oracle/* /usr/local/tomcat/lib/.
fi

# If it exists, include local.start.sh

if [ -f /mnt/local/local.start.sh ]
then
  /bin/sh /mnt/local/local.start.sh
fi

# Redirect logs to stdout and stderr for docker reasons.
ln -sf /dev/stdout /usr/local/tomcat/logs/access_log
ln -sf /dev/stderr /usr/local/tomcat/logs/error_log

/usr/local/tomcat/bin/catalina.sh run
