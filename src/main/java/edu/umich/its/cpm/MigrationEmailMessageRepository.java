package edu.umich.its.cpm;

import java.util.List;
import java.sql.Timestamp;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface MigrationEmailMessageRepository extends CrudRepository<MigrationEmailMessage, String> {
	/**
	 * validate the database connection
	 *
	 * @return 1 if database connection works as expected
	 */
	@Query("SELECT count(*) from MigrationEmailMessage")
	public int validate();

	/**
	 * Finds next unprocessed message migration request
	 * @return
	 */
	@Query("SELECT message FROM MigrationEmailMessage message WHERE message.start_time is null order by message.migration_id desc")
	public List<MigrationEmailMessage> findNextNewMigrationEmailMessage();
	
	/**
	 * set the start time for message migration
	 * @param message_id
	 * @param t
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update MigrationEmailMessage message set message.start_time = ?#{[1]} where message.message_id = ?#{[0]}")
	public void setMigrationMessageStartTime(String message_id, Timestamp t);
	
	/**
	 * set the end time for message migration
	 * @param message_id
	 * @param t
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update MigrationEmailMessage message set message.end_time = ?#{[1]} where message.message_id = ?#{[0]}")
	public void setMigrationMessageEndTime(String message_id, Timestamp t);
	
	/**
	 * set status for message migration
	 * @param message_id
	 * @param status
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update MigrationEmailMessage message set message.status = ?#{[1]} where message.message_id = ?#{[0]}")
	public void setMigrationMessageStatus(String message_id, String status);
	
	/**
	 * select all message items for certain migration record
	 * @param migrationId
	 */
	@Query("select count(*) from MigrationEmailMessage message where message.migration_id= ?#{[0]}")
	public int getMigrationMessageCountForMigration(String migrationId);
	
	/**
	 * select all finished message items for certain migration record
	 * @param migrationId
	 */
	@Query("select count(*) from MigrationEmailMessage message where message.migration_id= ?#{[0]} and message.end_time is not null")
	public int getFinishedMigrationMessageCountForMigration(String migrationId);
	
	/**
	 * select the last message migration time for given migration
	 * @param migrationId
	 */
	@Query("SELECT MAX(message.end_time) FROM MigrationEmailMessage message where message.migration_id= ?#{[0]}")
	public Timestamp getLastItemEndTimeForMigration(String migrationId);
	
	/**
	 * select all message item status for given migration
	 * @param migrationId
	 */
	@Query("SELECT message FROM MigrationEmailMessage message WHERE message.migration_id= ?#{[0]} order by message.start_time asc")
	public List<MigrationEmailMessage> getAllItemStatusForMigration(String migrationId);
	
}