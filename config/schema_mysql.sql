-- create database cpm
create database cpm default character set utf8;

grant all on cpm.* to cpmuser@'localhost' identified by 'cpmpassword';

grant all on cpm.* to cpmuser@'127.0.0.1' identified by 'cpmpassword';

flush privileges;


-- create table migration
CREATE TABLE MIGRATION (
ID INT NOT NULL AUTO_INCREMENT,
SITE_ID VARCHAR(99) NOT NULL,
SITE_OWNER VARCHAR(99) NOT NULL,
MIGRATED_BY VARCHAR(99) NOT NULL,
START_TIME TIMESTAMP,
STOP_TIME TIMESTAMP,
TOOL VARCHAR(99) NOT NULL,
DESTINATION_TYPE VARCHAR(99) NOT NULL,
DESTINATION_URL VARCHAR(99),
PRIMARY KEY (ID));