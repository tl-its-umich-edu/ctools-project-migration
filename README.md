# CTools Project Migration

## Tool Description:

This project servers as a standalone tool that helps CTools user to transfer CTools project site contents (Resources files, Email Archive messages, etc.) to outside environments 
(Box, Google, local disk, etc). Here is a brief description of the migration workflow:

* Upon login, the user will be presented with a list of CTools project sites where he has Owner role, and tools (Resources, Email Archive, et) within those sites;
* User picks the tool(s) he want to migrate, choose the migration destination,  and start the migrate process
* The migration record is recorded in the tool database, with information of related CTools project site, tools to migrate, destination, start and end time;
* The tool UI shows the migration status based on the migration records in database.

## Technologies used:

### Server side: 

Spring Boot (https://github.com/spring-projects/spring-boot) makes it easy to create Spring-powered, production-grade applications and services with minimum efforts.

* Spring JPA provides access to data repository
* Spring actuator adds several production grade services for server monitoring
* Spring web provides RESTful web service

### UI: AngularJS

## Configuration Settings

* copy config/application_template.properties to application.properties;
* update the configuration varibles with local settings;

## database connection

* The Spring Framework provides extensive support for working with various SQL databases. Choose desired SQL database for data storage;
* Put corresponding JDBC dependency into pom.xml file. For example, use mysql-connector-java for MySQL connection
* Example MySQL scripts are provided in config/schema_mysql.sql. Run the script to create database and migration table inside.
* Put database connection information into config/application.properties

## Run the code with Tomcat 7 embedded:
* run 'mvn spring-boot:run'. This will compile the code and start embedded tomcat server.
* http://localhost:8080/health returns server status
* http://localhost:8080/?testUser=<uniqname> is the landing page of the project migration tool and enable `allow.testUser.urlOverride = true` in application.properties
TALK ABOUT mvn clean
TALK ABOUT setting port explicitly


## Deploy as war file:
* run 'mvn package' to generate ctools-project-migration-VERSION.war file, in /target folder
* rename the war file as ctools-project-migration.war and copy into tomcat/webapps folder
* access the tool via https://SERVER:PORT/ctools-project-migration/
* The server port can be changed by specifying a port explicitly in
  application.properties.  E.g. server.port = 8090

## Testing with JMeter
* JMeter scripts are included, which can be run from either JMeter UI or from maven
* "mvn clean verify" will run the JMeter script for default profile, according to settings in pom.xml
* Allow people from certain MCommunity Group specify end user id in the tool URL. Please refer to application_template.properties file for the configuration settings.

## JUnit Test
* By Default the Junit test are skipped when issue `mvn package` command inorder to run the build including Junit test do `mvn package -DskipTests=false`

## Unit Testing Email Archive Messages
 Testing each Email message is returned as RFC822 format for Email Archive migration to the Google Groups.
     The `EmailFormatter.java`class takes a single email message in the string json format. 
     The EmailFormatter class can give various email formats like rfc822 complaint format, Mbox format etc. For RFC822 email
     format generation takes all the headers provided by ctools and pruning headers that contains `content-type`. For email attachments,
     ctools is providing attachment link instead of Base64 encoded string. So we are extracting the content from the attachment
     and encode the content to base64. We are actually getting the base64 encoding using java mail service. We using the java
     mail service for generating the email text and appending the existing header from ctools with email text from 
     mail services. The pruning of email headers from ctools containing `content-type` is done as these headers are created 
     when generating the email text from  mail service so if having then they will be like duplicate headers and email messages
     will not be imported to Google groups.
 
* under `/src/test/java` add the values for ctools url/password/username so could access the email messages from the Ctools.
* Sample Email messages are added under `/src/test/java` as `message.json` or `message_1.json` used for the junit testing.
  For more example of email message go to https://ctqa.dsc.umich.edu/direct/mailarchive/describe and access the message
* `EmailFormatterTest.java` has various test associated so that a valid RFC822 email message is returned. Simlarly
   `AttachmentHandlerTest.java` has test related to email attachment content.


