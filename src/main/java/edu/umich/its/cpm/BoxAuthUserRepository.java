package edu.umich.its.cpm;

import java.util.List;

import java.sql.Timestamp;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface BoxAuthUserRepository extends CrudRepository<BoxAuthUser, String> {
	/**
	 * validate the database connection
	 *
	 * @return 1 if database connection works as expected
	 */
	@Query("SELECT count(*) from BoxAuthUser")
	public int validate();

	/**
	 * Finds BoxAuthUser record with userId
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT u FROM BoxAuthUser u WHERE u.userId = ?#{[0]}")
	List<BoxAuthUser> findBoxAuthUserByUserId(String userId);

	/**
	 * Finds BoxAuthUser with userId
	 * 
	 * @param userId
	 * @return A BoxAuthUser with userId
	 */
	@Query("SELECT u FROM BoxAuthUser u WHERE u.userId = ?#{[0]}")
	public BoxAuthUser findOne(String userId);
	
	/**
	 * Finds BoxAuthUser record with state string
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT u FROM BoxAuthUser u WHERE u.state = ?#{[0]}")
	List<BoxAuthUser> findBoxAuthUserByState(String state);
	
	/**
	 * Finds BoxAuthUser access token
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT accessToken FROM BoxAuthUser u WHERE u.userId = ?#{[0]}")
	String findBoxAuthUserAccessToken(String userId);
	
	/**
	 * Finds BoxAuthUser refresh token
	 * 
	 * @param userId
	 * @return
	 */
	@Query("SELECT refreshToken FROM BoxAuthUser u WHERE u.userId = ?#{[0]}")
	String findBoxAuthUserRefreshToken(String userId);
	
	/************** updates *************/

	/**
	 * Update the BoxAuthUser state field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update BoxAuthUser u set u.state = ?#{[0]} where u.userId = ?#{[1]}")
	public int setBoxAuthUserState(String state, String userId);
	
	/**
	 * Update the BoxAuthUser accessToken field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update BoxAuthUser u set u.accessToken = ?#{[0]} where u.userId = ?#{[1]}")
	public int setBoxAuthUserAccessToken(String accessToken, String userId);
	
	/**
	 * Update the BoxAuthUser refreshToken field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update BoxAuthUser u set u.refreshToken = ?#{[0]} where u.userId = ?#{[1]}")
	public int setBoxAuthUserRefreshToken(String refreshToken, String userId);
	

	/************** delete *************/
	
	/**
	 * delete BoxAuthUser by userId
	 * 
	 * @param userId
	 * @return
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("delete FROM BoxAuthUser u WHERE u.userId = ?#{[0]}")
	public void deleteBoxAuthUser(String userId);
	
	/**
	 * delete the BoxAuthUser accessToken field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update BoxAuthUser u set u.accessToken = null where u.userId = ?#{[0]}")
	public void deleteBoxAuthUserAccessToken(String userId);
	
	/**
	 * delete the BoxAuthUser refreshToken field
	 */
	@Transactional
	@Modifying(clearAutomatically = false)
	@Query("update BoxAuthUser u set u.refreshToken = null where u.userId = ?#{[0]}")
	public void deleteBoxAuthUserRefreshToken(String userId);
}
