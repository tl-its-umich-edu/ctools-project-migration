# CTools Project Migration

## Tool Description:

This project servers as a standalone tool that helps CTools user to transfer CTools project site contents (Resources files, Email Archive messages, etc.) to outside environments 
(Box, Google, local disk, etc). Here is a brief description of the migration workflow:

* Upon login, the user will be presented with a list of CTools project sites where he has "site.upd" permission, and tools (Resources, Email Archive, et) within those sites;
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
* http://localhost:8080/ is the landing page of the project migration tool

## Deploy as war file:
* run 'mvn package' to generate ctools-project-migration-VERSION.war file, in /target folder
* rename the war file as ctools-project-migration.war and copy into tomcat/webapps folder
* access the tool via https://SERVER:PORT/ctools-project-migration/ 


