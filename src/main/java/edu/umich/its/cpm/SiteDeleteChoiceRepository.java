package edu.umich.its.cpm;

import java.util.List;

import java.sql.Timestamp;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface SiteDeleteChoiceRepository extends CrudRepository<SiteDeleteChoice, String> {
	/**
	 * validate the database connection
	 *
	 * @return 1 if database connection works as expected
	*/
	@Query("SELECT count(*) FROM SiteDeleteChoice")
	public int validate();
	
	/**
	 * Finds site delete choice by user
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT c FROM SiteDeleteChoice c WHERE c.userId = ?#{[0]}")
	List<SiteDeleteChoice> findSiteDeleteChoiceForUser(String userId);

	/**
	 * Finds site delete choice with CTools site id
	 * 
	 * @param userId
	 * @return A BoxAuthUser with userId
	 */
	@Query("SELECT c FROM SiteDeleteChoice c WHERE c.siteId = ?#{[0]}")
	public SiteDeleteChoice findSiteDeleteChoiceForSite(String siteId);
	
	/************** delete *************/
	
	/**
	 * delete SiteDeleteChoice entry
	 * 
	 * @param siteId
	 * @return
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("DELETE FROM SiteDeleteChoice c WHERE c.siteId = ?#{[0]}")
	public void deleteSiteDeleteChoice(String siteId);
}
