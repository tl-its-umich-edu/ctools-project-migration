-- create database cpm
create database cpm default character set utf8;

grant all on cpm.* to cpmuser@'localhost' identified by 'cpmpassword';

grant all on cpm.* to cpmuser@'127.0.0.1' identified by 'cpmpassword';

flush privileges;


-- create table migration
CREATE TABLE MIGRATION (
MIGRATION_ID INT NOT NULL AUTO_INCREMENT,
SITE_ID VARCHAR(99) NOT NULL,
SITE_NAME VARCHAR(99) NOT NULL,
TOOL_ID VARCHAR(99) NOT NULL,
TOOL_NAME VARCHAR(99) NOT NULL,
MIGRATED_BY VARCHAR(99) NOT NULL,
START TIMESTAMP NOT NULL,
END TIMESTAMP,
DESTINATION_TYPE VARCHAR(99) NOT NULL,
DESTINATION_URL VARCHAR(99),
PRIMARY KEY (MIGRATION_ID));