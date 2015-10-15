package edu.umich.its.cpm;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import java.sql.Timestamp;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.GenerationType;

import lombok.Getter;
import lombok.Setter;

@Entity
public class Migration {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "MIGRATION_ID", table = "MIGRATION")
	@Getter
	@Setter
	private Integer migration_id;

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

	@Column(name = "START", columnDefinition = "TIMESTAMP NOT NULL", table = "MIGRATION")
	@Getter
	@Setter
	private Timestamp start;

	@Column(name = "END", columnDefinition = "TIMESTAMP", table = "MIGRATION")
	@Getter
	@Setter
	private Timestamp end;

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
			String tool_name, String migrated_by, Timestamp start,
			Timestamp end, String destination_type, String destination_url) {
		this.site_id = site_id;
		this.site_name = site_name;
		this.tool_id = tool_id;
		this.tool_name = tool_name;
		this.migrated_by = migrated_by;
		this.start = start;
		this.end = end;
		this.destination_type = destination_type;
		this.destination_url = destination_url;
	}

	@Override
	public String toString() {
		return String.format("Migration[migration_id=%d, " + "site_id='%s',"
				+ "site_name='%s'," + "tool_id='%s'," + "tool_name='%s',"
				+ "migrated_by='%s'," + "start='%s'," + "end='%s',"
				+ "destination_type='%s'," + "destination_url='%s'" + "]",
				migration_id, site_id, site_name, tool_id, tool_name,
				migrated_by, start, end, destination_type, destination_url);
	}
}
