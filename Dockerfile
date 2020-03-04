FROM tomcat:8.5-jdk8

MAINTAINER Teaching and Learning <its.tl.dev@umich.edu>

RUN apt-get update \
 && apt-get install -y maven git

ENV JAVA_HOME=/usr/local/openjdk-8/jre

WORKDIR /tmp

# Copy CPM code to local directory for building
COPY . /tmp

# Build CPM and place the resulting war in the tomcat dir.
RUN mvn clean install \
	&& mv ./target/ctools-project-migration-0.1.0.war /usr/local/tomcat/webapps/ROOT.war

# Remove unnecessary build dependencies.
RUN apt-get remove -y maven git \
 && apt-get autoremove -y

WORKDIR /usr/local/tomcat/webapps

RUN rm -rf ROOT docs examples host-manager manager

EXPOSE 8080
EXPOSE 8009

RUN mkdir /usr/local/tomcat/home/

### change directory owner, as openshift user is in root group.
RUN chown -R root:root /usr/local/tomcat/lib /usr/local/tomcat/logs \
	/usr/local/tomcat/home /var/lock /var/run/lock

### Modify perms for the openshift user, who is not root, but part of root group.
#RUN chmod 777 /usr/local/tomcat/conf /usr/local/tomcat/conf/webapps
RUN chmod g+rw /usr/local/tomcat/conf /usr/local/tomcat/logs /usr/local/tomcat/webapps \
        /usr/local/tomcat/home /usr/local/tomcat/lib \
	/usr/local/tomcat/conf/server.xml /var/lock /var/run/lock

### Start script incorporates config files and sends logs to stdout ###
COPY start.sh /usr/local/bin
RUN chmod 755 /usr/local/bin/start.sh
CMD /usr/local/bin/start.sh

