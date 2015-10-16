# CTools Project Migration

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


