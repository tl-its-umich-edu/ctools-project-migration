package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonRawValue;


@Table(name = "MIGRATION_EMAIL_MESSAGE")
@Entity
public class MigrationEmailMessage {

	private static final Logger log = LoggerFactory
			.getLogger(MigrationEmailMessage.class);

	/**
	 * primary key field
	 * 
	 */
	@Id
	@Column(name = "MESSAGE_ID", columnDefinition = "VARCHAR(150) NOT NULL")
	@Getter
	@Setter
	private String message_id;

	/**
	 * a foreign key to the migration table
	 */
	@Column(name = "MIGRATION_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String migration_id;
	
	/**
	 * user who started the migration
	 */
	@Column(name = "USER_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String user_id;

	/**
	 * the target Google Group ID
	 */
	@Column(name = "GOOGLE_GROUP_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String google_group_id;

	/**
	 * the message JSON value
	 */
	@Column(name = "JSON", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String json;

	@Column(name = "START_TIME", columnDefinition = "TIMESTAMP NOT NULL")
	@Getter
	@Setter
	private Timestamp start_time;

	@Column(name = "END_TIME", columnDefinition = "TIMESTAMP")
	@Getter
	@Setter
	private Timestamp end_time;

	@Column(name = "STATUS", columnDefinition = "LOB")
	@Getter
	@Setter
	@JsonRawValue
	private String status;

	protected MigrationEmailMessage() {
	}

	public MigrationEmailMessage(String message_id, String migration_id, 
			String user_id, String google_group_id,
			String json, Timestamp start_time,
			Timestamp end_time, String status) {
		this.message_id = migration_id+":"+message_id;
		this.migration_id = migration_id;
		this.user_id = user_id;
		this.google_group_id = google_group_id;
		this.json = json;
		this.start_time = start_time;
		this.end_time = end_time;
		this.status = status;
	}

	@Override
	public String toString() {
		String s = String.format("MigrationEmailMessage[message_id=%s,"
				+ "migration_id=%s, " + "user_id=%s, " + "google_group_id=%s, " 
				+ "json=%s, " + "start_time=%s, " + "end_time='%s',"
				+ "status='%s'" + "]", message_id, migration_id, user_id,
				google_group_id, json,
				start_time, end_time, status);
		return s;
	}
}
