package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import com.fasterxml.jackson.annotation.JsonRawValue;

@Entity
public class MigrationBoxFile {

	private static final Logger log = LoggerFactory
			.getLogger(MigrationBoxFile.class);

	/**
	 * primary key field
	 * 
	 */
	@Id
	@Column(name = "ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String id;

	/**
	 * a foreign key to the migration table
	 */
	@Column(name = "MIGRATION_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String migration_id;
	
	/**
	 * user who started the migration
	 */
	@Column(name = "USER_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String user_id;

	/**
	 * the target Box folder id
	 */
	@Column(name = "BOX_FOLDER_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String box_folder_id;

	/**
	 * the file type
	 */
	@Column(name = "TYPE", columnDefinition = "VARCHAR(99) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String type;

	/**
	 * the file title
	 */
	@Column(name = "TITLE", columnDefinition = "VARCHAR(256) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String title;

	/**
	 * the CTools web link resource url
	 */
	@Column(name = "WEB_LINK_URL", columnDefinition = "VARCHAR(2000)", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String web_link_url;

	/**
	 * the CTools file access_url
	 */
	@Column(name = "FILE_ACCESS_URL", columnDefinition = "VARCHAR(2000) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String file_access_url;

	/**
	 * the CTools file description
	 */
	@Column(name = "DESCRIPTION", columnDefinition = "VARCHAR(2000)", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String description;

	/**
	 * the file creator
	 */
	@Column(name = "AUTHOR", columnDefinition = "VARCHAR(256) NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String author;

	/**
	 * the CTools file description
	 */
	@Column(name = "COPYRIGHT_ALERT", columnDefinition = "VARCHAR(2000)", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private String copyright_alert;

	/**
	 * the CTools file size
	 */
	@Column(name = "FILE_SIZE", columnDefinition = "NUMBER(10)", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private long file_size;

	@Column(name = "START_TIME", columnDefinition = "TIMESTAMP NOT NULL", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private Timestamp start_time;

	@Column(name = "END_TIME", columnDefinition = "TIMESTAMP", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	private Timestamp end_time;

	@Column(name = "STATUS", columnDefinition = "LOB", table = "MIGRATION_BOX_FILE")
	@Getter
	@Setter
	@JsonRawValue
	private String status;

	protected MigrationBoxFile() {
	}

	public MigrationBoxFile(String migration_id, String user_id, String box_folder_id,
			String type, String title, String web_link_url,
			String file_access_url, String description, String author,
			String copyright_alert, long file_size, Timestamp start_time,
			Timestamp end_time, String status) {
		this.id = UUID.randomUUID().toString();
		this.migration_id = migration_id;
		this.user_id = user_id;
		this.box_folder_id = box_folder_id;
		this.type = type;
		this.title = title;
		this.web_link_url = web_link_url;
		this.file_access_url = file_access_url;
		this.description = description;
		this.author = author;
		this.copyright_alert = copyright_alert;
		this.file_size = file_size;
		this.start_time = start_time;
		this.end_time = end_time;
		this.status = status;
	}

	@Override
	public String toString() {
		String s = String.format("MigrationBoxFile[id=%s,"
				+ "migration_id=%s, " + "user_id=%s, " + "box_folder_id=%s, " + "type=%s, "
				+ "title=%s, " + "web_link_url=%s, " + "file_access_url=%s, "
				+ "description=%s, " + "author=%s, " + "copyright_alert=%s, "
				+ "file_size=%d, " + "start_time=%s, " + "end_time='%s',"
				+ "status='%s'" + "]", id, migration_id, user_id, box_folder_id, type,
				title, web_link_url, file_access_url, description, author,
				copyright_alert, file_size, start_time, end_time, status);
		return s;
	}
}
