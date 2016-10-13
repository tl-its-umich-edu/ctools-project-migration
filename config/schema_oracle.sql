-- create table migration
create table migration (
migration_id VARCHAR(99) NOT NULL,
bulk_migration_id VARCHAR(99),
bulk_migration_name VARCHAR(99),
site_id VARCHAR(99) NOT NULL,
site_name VARCHAR(99) NOT NULL,
tool_id VARCHAR(99) NOT NULL,
tool_name VARCHAR(99) NOT NULL,
migrated_by VARCHAR(99) NOT NULL,
start_time TIMESTAMP NOT NULL,
end_time TIMESTAMP,
destination_type VARCHAR(99) NOT NULL,
destination_url VARCHAR(99),
status CLOB,
PRIMARY KEY (MIGRATION_ID));

create table migration_box_file (
id	VARCHAR(99)    NOT NULL,
migration_id VARCHAR(99) NOT NULL,
user_id VARCHAR(99) NOT NULL,
box_folder_id VARCHAR(99) NOT NULL,
type VARCHAR(99) NOT NULL,
title VARCHAR(256) NOT NULL,
web_link_url VARCHAR(2000),
file_access_url VARCHAR(2000) NOT NULL,
description VARCHAR(2000),
author VARCHAR(2000) NOT NULL,
copyright_alert VARCHAR(2000),
file_size number(10) NOT NULL,
start_time TIMESTAMP,
end_time TIMESTAMP,
status CLOB,
PRIMARY KEY (id));

create table migration_email_message (
message_id	VARCHAR(150) NOT NULL,
migration_id VARCHAR(99) NOT NULL,
user_id VARCHAR(99) NOT NULL,
google_group_id VARCHAR(99) NOT NULL,
json CLOB NOT NULL,
start_time TIMESTAMP,
end_time TIMESTAMP,
status CLOB,
PRIMARY KEY (message_id));

-- create table box_auth_user
create table box_auth_user (
user_id VARCHAR(99) NOT NULL,
state VARCHAR(99),
access_token VARCHAR(99),
refresh_token VARCHAR(99),
PRIMARY KEY (user_id));

-- create table SITE_DELETE_CHOICE
CREATE TABLE SITE_DELETE_CHOICE (
SITE_ID VARCHAR(99) NOT NULL,
USER_ID VARCHAR(99) NOT NULL,
CONSENT_TIME TIMESTAMP,
PRIMARY KEY (SITE_ID));

-- create table SITE_TOOL_EXCEPT_CHOICE
CREATE TABLE SITE_TOOL_EXEMPT_CHOICE (
EXEMPT_ID VARCHAR(99) NOT NULL,
SITE_ID VARCHAR(99) NOT NULL,
TOOL_ID VARCHAR(99) NOT NULL,
USER_ID VARCHAR(99) NOT NULL,
CONSENT_TIME TIMESTAMP,
PRIMARY KEY (EXEMPT_ID));