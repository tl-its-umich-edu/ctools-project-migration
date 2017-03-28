FROM centos

# install tomcat and run cpm war file.
# setup for basic auth to allow setting remote user.

ENV JAVA_VERSION 8u31
ENV BUILD_VERSION b13

# Upgrading system
RUN yum -y upgrade
RUN yum -y install wget

# Downloading & Config Java 8
RUN wget --no-cookies --no-check-certificate --header "Cookie: oraclelicense=accept-securebackup-cookie" "http://download.oracle.com/otn-pub/java/jdk/$JAVA_VERSION-$BUILD_VERSION/jdk-$JAVA_VERSION-linux-x64.rpm" -O /tmp/jdk-8-linux-x64.rpm
RUN yum -y install /tmp/jdk-8-linux-x64.rpm
RUN alternatives --install /usr/bin/java jar /usr/java/latest/bin/java 200000
RUN alternatives --install /usr/bin/javaws javaws /usr/java/latest/bin/javaws 200000
RUN alternatives --install /usr/bin/javac javac /usr/java/latest/bin/javac 200000

ENV CATALINA_HOME /usr/local/tomcat
ENV PATH $CATALINA_HOME/bin:$PATH

RUN mkdir -p "$CATALINA_HOME"
WORKDIR $CATALINA_HOME

ENV TOMCAT_MAJOR 8
ENV TOMCAT_VERSION 8.0.41
ENV TOMCAT_TGZ_URL https://www.apache.org/dist/tomcat/tomcat-$TOMCAT_MAJOR/v$TOMCAT_VERSION/bin/apache-tomcat-$TOMCAT_VERSION.tar.gz

RUN set -x \
    && curl -fSL "$TOMCAT_TGZ_URL" -o tomcat.tar.gz \
    && curl -fSL "$TOMCAT_TGZ_URL.asc" -o tomcat.tar.gz.asc \
    && tar -xvf tomcat.tar.gz --strip-components=1 \
    && rm bin/*.bat \
    && rm tomcat.tar.gz*

# should do this 
#    && gpg --verify tomcat.tar.gz.asc \

# main web port
EXPOSE 8080

### for debugging  Use nonstandard port to not conflict with other applications
EXPOSE 8090
ENV JPDA_ADDRESS="8090"
ENV JPDA_TRANSPORT="dt_socket"

#install Spring Boot artifact
VOLUME /tmp
# install the configuration
COPY application.properties /usr/local/tomcat/home/application.properties
# install the war file.
RUN rm -rf $CATALINA_HOME/webapps/ROOT*
ADD ./*.war $CATALINA_HOME/webapps/ROOT.war
# update tomcat with local basic auth
ADD ./web.xml $CATALINA_HOME/conf/web.xml
ADD ./tomcat-users.xml $CATALINA_HOME/conf/tomcat-users.xml
CMD ["catalina.sh", "jpda", "run"]

### Start in docker with:
# docker run -p 8080:8080 -p 8090:8090 cpm_a

#end
