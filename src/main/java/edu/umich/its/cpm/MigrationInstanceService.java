package edu.umich.its.cpm;

import org.json.JSONObject;
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
import java.sql.Timestamp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.protocol.HttpContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Component
public class MigrationInstanceService {
	
	private static final Logger log = LoggerFactory
			.getLogger(MigrationInstanceService.class);
	
	@Autowired
	private MigrationTaskService migrationTaskService;
	
	@Autowired
	private BoxAuthUserRepository uRepository;

	@Autowired
	private MigrationBoxFileRepository fRepository;
	
	@Autowired
	private MigrationEmailMessageRepository eRepository;
	
	@Autowired
	private MigrationRepository mRepository;
	
	@Autowired
	private Environment env;
	
	/**
	 * check the setting of parallel processing thread number
	 * @return
	 */
	private int getMaxParallelThreadNum()
	{
		if (env.getProperty(Utils.MAX_PARALLEL_THREADS_PROP) != null)
		{
			try
			{
				// return the setting in property file
				return Integer.parseInt(env.getProperty(Utils.MAX_PARALLEL_THREADS_PROP));
			}
			catch (NumberFormatException e)
			{
				// log error and return default value
				log.error(Utils.MAX_PARALLEL_THREADS_PROP + " property should have integer value. ");
				return Utils.MAX_PARALLEL_THREADS_NUM;
			}
		}
		// return default value
		return Utils.MAX_PARALLEL_THREADS_NUM;
	}
	
	@Async
	public void runProcessingThread() throws InterruptedException {
		
		log.info("Box Migration Processing thread is running");
		
		int threadNum = getMaxParallelThreadNum();
		
		List<Future<String>> futureList = new ArrayList<Future<String>>();

		while (true)
		{

			// delay for 5 seconds
			Thread.sleep(5000L);
	        
			// looping through resource request
			List<MigrationBoxFile> bFiles = fRepository.findNextNewMigrationBoxFile();
			if (bFiles != null && bFiles.size() > 0)
			{
				// get right HttpContext object
				HttpContext httpContext = Utils.login_become_admin(env);
				
				// process with the Box upload request
				for(MigrationBoxFile bFile : bFiles)
				{	
					if (futureList.size() < threadNum)
					{
						// mark the file as processed
						fRepository.setMigrationBoxFileStartTime(bFile.getId(), new Timestamp(System.currentTimeMillis()));
						
						futureList.add( migrationTaskService.uploadBoxFile(bFile.getId(),
								bFile.getUser_id(), bFile.getType(),
								bFile.getBox_folder_id(), bFile.getTitle(),
								bFile.getWeb_link_url(), bFile.getFile_access_url(), bFile.getDescription(),
								bFile.getAuthor(), bFile.getCopyright_alert(), bFile.getFile_size(), httpContext));
					}
				}
			}
			
			// looping through resource request
			List<MigrationEmailMessage> messages = eRepository.findNextNewMigrationEmailMessage();
			// process with the message upload request
			for(MigrationEmailMessage message : messages)
			{	
				if (futureList.size() < threadNum )
				{
					// mark the file as processed
					eRepository.setMigrationMessageStartTime(message.getMessage_id(), new Timestamp(System.currentTimeMillis()));
					
					//call to microservice to upload message to Google Groups
					futureList.add(migrationTaskService.uploadMessageToGoogleGroup(message));
				}
			}
			
		    
			// get a cloned list, in case we need to remove the finished async tasks from the original list
			List<Future<String>> futureListClone = new ArrayList<Future<String>>();
			futureListClone.addAll(futureList);
			
			for (Future<String> future : futureListClone) {
				try {
					// get the status of asynchronize processed Box upload request
					if (future.isDone())
					{
						// finished, remove the task from the future list queue
						futureList.remove(future);
					}
				} catch (Exception e) {
					log.error("runProcessingThread " + e.getMessage());
				}
			}
			
			// if all itemized migration finishes, 
			// update the parent migration record for status and end time
			updateMigrationStatusAndEndTime();
		}
	}

	/**
	 * check to see whether all itemized Box migration finishes, 
	 * so that the parent record can be updated
	 */
	private void updateMigrationStatusAndEndTime() {
		// this is a task to iterate through the non-finished site migration record
		// check whether all child items has been migrated
		// if so, set the end time of the site migration, and set the aggregated status
		List<Migration> allOngoingMigrations = mRepository.findMigrating();
		for (Migration migration : allOngoingMigrations)
		{
			String mId = migration.getMigration_id();
			String destination_type = migration.getDestination_type();
			if (Utils.MIGRATION_TYPE_BOX.equals(destination_type))
			{
				// for Box file migration
				updateBoxMigrationTimeAndStatus(mId);
			}
			else if (Utils.MIGRATION_TYPE_GOOGLE.equals(destination_type))
			{
				// for Google Groups email migration	
				updateMessageMigrationTimeAndStatus(mId);
			}
		}
	}

	/**
	 * if all resource items within the migration is finished
	 * update the migration end time with the last end time of items
	 * update the migration status with the aggregation of item status
	 * @param mId
	 */
	private void updateBoxMigrationTimeAndStatus(String mId) {
		int allItemCount = fRepository.getMigrationBoxFileCountForMigration(mId);
		int allFinishedItemCount = fRepository.getFinishedMigrationBoxFileCountForMigration(mId);
		if (allItemCount > 0 && allItemCount == allFinishedItemCount )
		{
			// all the items within the migration is finished
			// update the end time of the parent record
			Timestamp lastItemMigrationTime = fRepository.getLastItemEndTimeForMigration(mId);
			mRepository.setMigrationEndTime(lastItemMigrationTime, mId);
			
			// update the status of the parent record
			List<MigrationBoxFile> mFileList = fRepository.getAllItemStatusForMigration(mId);
			// parse the string into JSON object
			List<MigrationFileItem> itemStatusList = new ArrayList<MigrationFileItem>();
			int itemStatusFailureCount = 0;
			for(MigrationBoxFile mFile : mFileList)
			{
				String status = mFile.getStatus();
				MigrationFileItem item = new MigrationFileItem(
						mFile.getFile_access_url(), 
						mFile.getTitle(), 
						status);
				itemStatusList.add(item);
				
				if (status.indexOf("Box upload successful for file") == -1)
				{
					// if there is error, status message won't have String "Box upload successful for file"
					itemStatusFailureCount++;
				}
			}
			
			// the HashMap object holds itemized status information
			HashMap<String, Object> statusMap = new HashMap<String, Object>();
			statusMap.put(Utils.MIGRATION_STATUS, itemStatusFailureCount == 0? Utils.STATUS_SUCCESS:Utils.STATUS_FAILURE);
			statusMap.put(Utils.MIGRATION_DATA, itemStatusList);

			// update the status of migration record
			mRepository.setMigrationStatus((new JSONObject(statusMap)).toString(), mId);
			
		}
	}
	
	/**
	 * if all message items within the migration is finished
	 * update the migration end time with the last end time of items
	 * update the migration status with the aggregation of item status
	 * @param mId
	 */
	private void updateMessageMigrationTimeAndStatus(String mId) {
		int allItemCount = eRepository.getMigrationMessageCountForMigration(mId);
		int allFinishedItemCount = eRepository.getFinishedMigrationMessageCountForMigration(mId);
		if (allItemCount > 0 && allItemCount == allFinishedItemCount )
		{
			// all the items within the migration is finished
			// update the end time of the parent record
			Timestamp lastItemMigrationTime = eRepository.getLastItemEndTimeForMigration(mId);
			mRepository.setMigrationEndTime(lastItemMigrationTime, mId);
			
			// update the status of the parent record
			List<MigrationEmailMessage> mMessageList = eRepository.getAllItemStatusForMigration(mId);
			// parse the string into JSON object
			List<MigrationFileItem> itemStatusList = new ArrayList<MigrationFileItem>();
			int itemStatusFailureCount = 0;
			for(MigrationEmailMessage mMessage : mMessageList)
			{
				String status = mMessage.getStatus();
				MigrationFileItem item = new MigrationFileItem(
						mMessage.getMessage_id(), 
						"", 
						status);
				itemStatusList.add(item);
				
				if (status.indexOf("Box upload successful for file") == -1)
				{
					// if there is error, status message won't have String "Box upload successful for file"
					itemStatusFailureCount++;
				}
			}
			
			// the HashMap object holds itemized status information
			HashMap<String, Object> statusMap = new HashMap<String, Object>();
			statusMap.put(Utils.MIGRATION_STATUS, itemStatusFailureCount == 0? Utils.STATUS_SUCCESS:Utils.STATUS_FAILURE);
			statusMap.put(Utils.MIGRATION_DATA, itemStatusList);

			// update the status of migration record
			mRepository.setMigrationStatus((new JSONObject(statusMap)).toString(), mId);
			
		}
	}

	/*************** Box Migration ********************/
	public String createUploadBoxInstance(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String boxFolderId, String migrationId, MigrationRepository repository) throws InterruptedException {
		
		MigrationFields mFields = new MigrationFields(env, request, response, userId, sessionAttributes, siteId, boxFolderId, migrationId, repository);
		
        return Utils.STATUS_SUCCESS;
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