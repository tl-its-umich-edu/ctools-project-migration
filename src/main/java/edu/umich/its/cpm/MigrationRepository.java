package edu.umich.its.cpm;

import java.util.List;

import java.sql.Timestamp;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface MigrationRepository extends CrudRepository<Migration, String> {
	/**
	 * validate the database connection
	 *
	 * @return 1 if database connection works as expected
	 */
	@Query("SELECT count(*) from Migration")
	public int validate();

	/**
	 * Finds finished migrations issued by given user
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT m FROM Migration m WHERE m.migrated_by like %:userId% and destination_type != 'box' order by m.start_time desc")
	List<Migration> findMigrations(@Param("userId") String userId);

	/**
	 * Finds finished migrations, with not-null stop time
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NOT NULL and m.migrated_by like %:userId% and destination_type != 'box' order by m.end_time desc")
	public List<Migration> findMigrated(@Param("userId") String userId);

	/**
	 * Finds ongoing migrations, done by given user
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NULL and m.migrated_by LIKE %:userId% and destination_type != 'box' order by m.start_time desc")
	public List<Migration> findMigrating(@Param("userId") String userId);
	
	/**
	 * Finds all ongoing migrations
	 * 
	 * @return
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NULL and destination_type != 'box' order by m.start_time desc")
	public List<Migration> findMigrating();

	/**
	 * Finds migration with migration_id
	 * 
	 * @param migration_id
	 * @return A migration with migration_id
	 */
	@Query("SELECT m FROM Migration m WHERE m.migration_id = ?#{[0]}")
	public Migration findOne(String migration_id);

	/**
	 * Finds list of migrations with site id
	 * 
	 * @param site_id
	 * @return A list of migration with site_id
	 */
	@Query("SELECT m FROM Migration m WHERE m.site_id = ?#{[0]} and destination_type != 'box' order by m.start_time desc")
	public List<Migration> findBySiteId(String site_id);
	
	/**
	 * get the status field
	 */
	@Query("select m.status from Migration m where m.id = ?#{[0]}")
	public String getMigrationStatus(String migrationId);

	/**
	 * Update the status field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update Migration m set m.status = ?#{[0]} where m.id = ?#{[1]}")
	public int setMigrationStatus(String status, String migrationId);

	/**
	 * Update the migration end time field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update Migration m set m.end_time = ?#{[0]} where m.id = ?#{[1]}")
	public int setMigrationEndTime(Timestamp t, String migrationId);

	/******** bulk migration ******/
	/**
	 * get all bulk migration ids
	 *
	 * @return
	 */
	@Query("SELECT m.bulk_migration_name, m.bulk_migration_id FROM Migration m where m.bulk_migration_id is not null group by m.bulk_migration_name, m.bulk_migration_id order by m.bulk_migration_name, m.bulk_migration_id desc")
	public List<Object[]> getAllBulkMigrations();

	/**
	 * get all ongoing bulk migration ids
	 *
	 * @return
	 */
	@Query("select m.bulk_migration_name, m.bulk_migration_id FROM Migration m where m.bulk_migration_id is not null and m.end_time is null group by m.bulk_migration_name, m.bulk_migration_id order by m.bulk_migration_name, m.bulk_migration_id desc")
	public List<Object[]> getOngoingBulkMigrations();

	/**
	 * get all migration with the bulk migration
	 * 
	 * @param bulk_migration_id
	 * @return
	 */
	@Query("SELECT m FROM Migration m where m.bulk_migration_id = ?#{[0]}")
	public List<Migration> getMigrationsInBulkUpload(String bulk_migration_id);

	/**
	 * get migration with the bulk migration id and site id
	 * 
	 * @param bulk_migration_id
	 * @param site_id
	 * @return
	 */
	@Query("SELECT m FROM Migration m where m.bulk_migration_id = ?#{[0]} and m.site_id = ?#{[1]}")
	public Migration getSiteMigrationInBulkUpload(String bulk_migration_id,
			String site_id);
	
	/**
	 * return site id for given migration
	 * @param migration_id
	 * @return
	 */
	@Query("SELECT m.site_id FROM Migration m where m.migration_id = ?#{[0]} and destination_type != 'box'")
	public String getMigrationSiteId(String migration_id);

}