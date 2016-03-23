package edu.umich.its.cpm;

import java.util.List;
import java.sql.Timestamp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

public interface MigrationRepository extends CrudRepository<Migration, String> {
	
	/**
	 * database ping
	 * 
	 * @return 1
	 */
	@Query(" %:ping_query%")
	public String ping(@Param("ping_query") String ping_query);
	
	/**
	 * Finds finished migrations issued by given user
	 * 
	 * @param userId
	 * @return 
	 */
	@Query("SELECT m FROM Migration m WHERE m.migrated_by = ?#{[0]} order by m.start_time desc")
	List<Migration> findMigrations(String userId);

	/**
	 * Finds finished migrations, with not-null stop time
	 * 
	 * @param userId
	 * @return 
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NOT NULL and m.migrated_by = ?#{[0]} order by m.end_time desc")
	public List<Migration> findMigrated(String userId);
	
	/**
	 * Finds ongoing migrations, done by given user
	 * 
	 * @param userId
	 * @return 
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NULL and m.migrated_by = ?#{[0]} order by m.start_time desc")
	public List<Migration> findMigrating(String userId);

	/**
	 * Finds migration with migration_id
	 * 
	 * @param migration_id
	 * @return A migration with migration_id
	 */
	//@Query("SELECT m FROM Migration m WHERE m.migration_id = ?#{[0]}")
	public Migration findOne(String migration_id);

	/**
	 * Finds list of migrations with site id
	 * 
	 * @param site_id
	 * @return A list of migration with site_id
	 */
	@Query("SELECT m FROM Migration m WHERE m.site_id = ?#{[0]} order by m.start_time desc")
	public List<Migration> findBySiteId(String site_id);
	
	/**
	 * Update the status field
	 */
	@Transactional
	@Modifying(clearAutomatically=false)
	@Query("update Migration m set m.status = ?#{[0]} where m.id = ?#{[1]}")
	public int setMigrationStatus(String status, String migrationId);
	

	/**
	 * Update the migration end time field
	 */
	@Transactional
	@Modifying(clearAutomatically=false)
	@Query("update Migration m set m.end_time = ?#{[0]} where m.id = ?#{[1]}")
	public int setMigrationEndTime(Timestamp t, String migrationId);

}