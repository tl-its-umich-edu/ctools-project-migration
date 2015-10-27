package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import java.sql.Timestamp;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.SequenceGenerator;
import javax.persistence.Id;
import javax.persistence.GenerationType;

import lombok.Getter;
import lombok.Setter;

@Entity
public class Migration {

	private static final Logger log = LoggerFactory
			.getLogger(Migration.class);
	@Id
	@Column(name = "MIGRATION_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String migration_id;

	@Column(name = "SITE_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String site_id;

	@Column(name = "SITE_NAME", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String site_name;

	@Column(name = "TOOL_NAME", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String tool_name;

	@Column(name = "TOOL_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String tool_id;

	@Column(name = "MIGRATED_BY", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String migrated_by;

	@Column(name = "START_TIME", columnDefinition = "TIMESTAMP NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private Timestamp start_time;

	@Column(name = "END_TIME", columnDefinition = "TIMESTAMP", table = "MIGRATION")
	@Getter
	@Setter
	private Timestamp end_time;

	@Column(name = "DESTINATION_TYPE", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private String destination_type;

	@Column(name = "DESTINATION_URL", columnDefinition = "VARCHAR(99)", table = "MIGRATION")
	@Getter
	@Setter
	private String destination_url;

	protected Migration() {
	}

	public Migration(String site_id, String site_name, String tool_id,
			String tool_name, String migrated_by, Timestamp start_time,
			Timestamp end_time, String destination_type, String destination_url) {
		this.migration_id = UUID.randomUUID().toString();
		log.info(this.migration_id);
		this.site_id = site_id;
		this.site_name = site_name;
		this.tool_id = tool_id;
		this.tool_name = tool_name;
		this.migrated_by = migrated_by;
		this.start_time = start_time;
		this.end_time = end_time;
		this.destination_type = destination_type;
		this.destination_url = destination_url;
	}

	@Override
	public String toString() {
		return String.format("Migration[migration_id=%d, " + "site_id='%s',"
				+ "site_name='%s'," + "tool_id='%s'," + "tool_name='%s',"
				+ "migrated_by='%s'," + "start_time='%s'," + "end_time='%s',"
				+ "destination_type='%s'," + "destination_url='%s'" + "]",
				migration_id, site_id, site_name, tool_id, tool_name,
				migrated_by, start_time, end_time, destination_type, destination_url);
	}
}
