-- create table migration
create table migration (
migration_id VARCHAR(99) NOT NULL,
site_id VARCHAR(99) NOT NULL,
site_name VARCHAR(99) NOT NULL,
tool_id VARCHAR(99) NOT NULL,
tool_name VARCHAR(99) NOT NULL,
migrated_by VARCHAR(99) NOT NULL,
start_time TIMESTAMP NOT NULL,
end_time TIMESTAMP,
destination_type VARCHAR(99) NOT NULL,
destination_url VARCHAR(99),
PRIMARY KEY (MIGRATION_ID));