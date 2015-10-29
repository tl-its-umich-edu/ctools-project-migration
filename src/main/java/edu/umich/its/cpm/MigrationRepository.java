package edu.umich.its.cpm;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;

public interface MigrationRepository extends CrudRepository<Migration, String> {
	List<Migration> findAll();

	/**
	 * Finds finished migrations, with not-null stop time
	 * 
	 * @param las
	 * @return A list of persons whose last name is an exact match with the
	 *         given last name. If no persons is found, this method returns an
	 *         empty list.
	 */
	@Query("SELECT m FROM Migration m WHERE m.end_time IS NOT NULL")
	public List<Migration> findMigrated();

	/**
	 * Finds migration with migration_id
	 * 
	 * @param migration_id
	 * @return A migration with migration_id
	 */
	// @Query("SELECT m FROM Migration m WHERE m.migration_id = ?#{[0]}")
	public Migration findOne(String migration_id);

	/**
	 * Finds list of migrations with site id
	 * 
	 * @param site_id
	 * @return A list of migration with site_id
	 */
	@Query("SELECT m FROM Migration m WHERE m.site_id = ?#{[0]}")
	public List<Migration> findBySiteId(String site_id);

}