package edu.umich.its.cpm;

import java.util.List;

import java.sql.Timestamp;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface SiteToolExemptRepository extends CrudRepository<SiteToolExemptChoice, String> {
	/**
	 * validate the database connection
	 *
	 * @return 1 if database connection works as expected
	 */
	@Query("SELECT count(*) from SiteToolExemptChoice")
	public int validate();

	/**
	 * Finds site tool exempt choice by user id
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT c FROM SiteToolExemptChoice c WHERE c.userId = ?#{[0]}")
	List<SiteToolExemptChoice> findSiteToolExemptChoiceForUser(String userId);
	
	/**
	 * Finds site tool exempt choice by site id
	 * 
	 * @param siteId
	 * @return
	 */
	@Query("SELECT c FROM SiteToolExemptChoice c WHERE c.siteId = ?#{[0]} and c.toolId = ?#{[1]}")
	List<SiteToolExemptChoice> findSiteToolExemptChoiceForSite(String siteId, String toolId);
	
	/************** delete *************/
	
	/**
	 * delete SiteToolExemptChoice entry
	 * 
	 * @param siteId
	 * @return
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("DELETE FROM SiteToolExemptChoice c WHERE c.siteId = ?#{[0]} AND c.toolId = ?#{[1]} ")
	public void deleteSiteToolExemptChoice(String siteId, String toolId);
}
