# CTools Project Migration

## Technologies used:

### Server side: 

Spring Boot (https://github.com/spring-projects/spring-boot) makes it easy to create Spring-powered, production-grade applications and services with minimum efforts.

* Spring JPA provides access to data repository
* Spring actuator adds several production grade services for server monitoring
* Spring web provides RESTful web service

### UI: AngularJS

## Run the code:

* Put database connection information into config/application.properties
* Example MySQL scripts are provided, for create database and tables 
* run 'mvn spring-boot:run'. This will compile the code and start embedded tomcat server.
* http://localhost:8080/health returns server status
* http://localhost:8080/ is the landing page of the project migration tool



