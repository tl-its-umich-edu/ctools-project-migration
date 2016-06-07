package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Service
@Component
public class MigrationInstanceService {
	
	private static final Logger log = LoggerFactory
			.getLogger(MigrationInstanceService.class);
	
	@Autowired
	private MigrationTaskService migrationTaskService;
	
	@Autowired
	private BoxAuthUserRepository uRepository;
	
	@Async
	public void runProcessingThread() throws InterruptedException {
		
		log.info("Box Migration Processing thread is running");
		
		List<Future<HashMap<String, String>>> futureList = new ArrayList<Future<HashMap<String, String>>>();

		while (true)
		{

			// delay for 5 seconds
			Thread.sleep(5000L);
	        
			// looping through
			HashMap<String, LinkedList<MigrationFields>> boxMigrationRequests = BoxUtils.getBoxMigrationRequests();
			Iterator<String> iUserIds = boxMigrationRequests.keySet().iterator();
			while (iUserIds.hasNext())
			{
				String userId = iUserIds.next();
				// get the migration request queue belongs to the user
				LinkedList<MigrationFields> lList = boxMigrationRequests.get(userId);
				
				MigrationFields mFields = lList.get(0);
				if (!mFields.getProcessed())
				{
					log.info("Box migration request processing user " + userId + " request queue length=" + lList.size());
					
					try
					{
						// if the first request for the user is not processed yet
						// process with the Box upload request
						futureList.add( migrationTaskService.uploadToBox(mFields.getEnv(), mFields.getRequest(), mFields.getResponse(), mFields.getUserId(), mFields.getSessionAttributes(), mFields.getSiteId(), mFields.getBoxFolderId(), mFields.getMigrationId(), mFields.getRepository(), uRepository));
						
						// mark this request as being processed
						mFields.setProcessed(true);
						lList.set(0, mFields);
						BoxUtils.setBoxMigrationRequestForUser(userId, lList);
					}
					catch (java.lang.InterruptedException e)
					{
						log.error("Error processing Box upload request from user " + userId + " for site " + mFields.getSiteId() + e.getMessage());
					}
				}	// if
			}	// while
		    
			boxMigrationRequests = BoxUtils.getBoxMigrationRequests();
			// get a cloned list, in case we need to remove the finished async tasks from the original list
			List<Future<HashMap<String, String>>> futureListClone = new ArrayList<Future<HashMap<String, String>>>();
			futureListClone.addAll(futureList);
			
			for (Future<HashMap<String, String>> future : futureListClone) {
				try {
					// get the status of asynchronize processed Box upload request
					if (future.isDone())
					{
						HashMap<String, String> rv = future.get();
						log.debug("***** future return userId=" + rv.get("userId") + " siteId=" + rv.get("siteId") + " status=" + rv.get("status"));
						
						// if the request is finished/processed, remove it from the queue
						String userId = rv.get("userId");
						LinkedList<MigrationFields> userMigrationRequests = boxMigrationRequests.get(userId);
						userMigrationRequests.remove();
						
						BoxUtils.setBoxMigrationRequestForUser(userId, userMigrationRequests);
						
						// finished, remove the task from the future list queue
						futureList.remove(future);
					}
				} catch (java.util.concurrent.ExecutionException e)
				{
					log.error(this + ":createUploadBoxInstance ", e);
				} catch (Exception e) {
					log.error(this + ":createUploadBoxInstance ", e);
				}
			} 
		}
	}
	
	public void createDownloadZipInstance(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String migrationId, MigrationRepository repository) throws InterruptedException {
		StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip");
		
        // async call
		migrationTaskService.downloadZippedFile(env, request, response, userId, sessionAttributes, siteId, migrationId, repository);
		stopWatch.stop();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip " + stopWatch.prettyPrint());
		
	}

	/*************** Box Migration ********************/
	public String createUploadBoxInstance(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String boxFolderId, String migrationId, MigrationRepository repository) throws InterruptedException {
		
		MigrationFields mFields = new MigrationFields(env, request, response, userId, sessionAttributes, siteId, boxFolderId, migrationId, repository);
		
		LinkedList<MigrationFields> userBoxMigrationRequests = BoxUtils.getBoxMigrationRequestForUser(userId);
		if (userBoxMigrationRequests == null)
		{
			// if there is no migration queue for this user yet, init a new queue, and add the request
			userBoxMigrationRequests = new LinkedList<MigrationFields>();
		}
		userBoxMigrationRequests.add(mFields);
		BoxUtils.setBoxMigrationRequestForUser(userId, userBoxMigrationRequests);
        
        return "success";
	}
	
	/**
	 * inner class to hold all required fields to create a migration request
	 * @author zqian
	 *
	 */
	class MigrationFields
	{
		private Environment env;
		
		public Environment getEnv() {
			return env;
		}

		public void setEnv(Environment env) {
			this.env = env;
		}

		public HttpServletRequest getRequest() {
			return request;
		}

		public void setRequest(HttpServletRequest request) {
			this.request = request;
		}

		public HttpServletResponse getResponse() {
			return response;
		}

		public void setResponse(HttpServletResponse response) {
			this.response = response;
		}

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}

		public HashMap<String, Object> getSessionAttributes() {
			return sessionAttributes;
		}

		public void setSessionAttributes(HashMap<String, Object> sessionAttributes) {
			this.sessionAttributes = sessionAttributes;
		}

		public String getSiteId() {
			return siteId;
		}

		public void setSiteId(String siteId) {
			this.siteId = siteId;
		}

		public String getBoxFolderId() {
			return boxFolderId;
		}

		public void setBoxFolderId(String boxFolderId) {
			this.boxFolderId = boxFolderId;
		}

		public String getMigrationId() {
			return migrationId;
		}

		public void setMigrationId(String migrationId) {
			this.migrationId = migrationId;
		}

		public MigrationRepository getRepository() {
			return repository;
		}

		public void setRepository(MigrationRepository repository) {
			this.repository = repository;
		}

		public Boolean getProcessed() {
			return processed;
		}

		public void setProcessed(Boolean processed) {
			this.processed = processed;
		}

		private HttpServletRequest request;
		
		private HttpServletResponse response;
		
		private String userId;
		
		private HashMap<String, Object> sessionAttributes;
		
		private String siteId;
		
		private String boxFolderId;
		
		private String migrationId;
		
		private MigrationRepository repository;
		
		private Boolean processed;
		
		/**
		 * constructor
		 * @param env
		 * @param request
		 * @param response
		 * @param userId
		 * @param sessionAttributes
		 * @param siteId
		 * @param boxFolderId
		 * @param migrationId
		 * @param repository
		 */
		public MigrationFields(Environment env, HttpServletRequest request,
				HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String boxFolderId, String migrationId, MigrationRepository repository)
		{
			this.env=env;
			this.request=request;
			this.response=response;
			this.userId=userId;
			this.sessionAttributes=sessionAttributes;
			this.siteId=siteId;
			this.boxFolderId=boxFolderId;
			this.migrationId=migrationId;
			this.repository=repository;
			// not processed yet
			this.processed = false;
		}
	}
}