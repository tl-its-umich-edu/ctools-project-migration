package edu.umich.its.cpm;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.Iterator;
import java.io.IOException;

import java.sql.Timestamp;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Component
class MigrationInstanceService {
	
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
				return 0;
			}
		}
		// return default value
		return Utils.MAX_PARALLEL_THREADS_NUM;
	}
	
	@Async
	public void runProcessingThreads() throws InterruptedException {
		
		log.info("Box Migration Processing thread is running");
		
		int threadNum = getMaxParallelThreadNum();
		
		// future list for Box migration tasks
		List<Future<String>> futureBoxList = new ArrayList<Future<String>>();
		
		// future list for Google Groups migration tasks
		List<Future<String>> futureGoogleGroupList = new ArrayList<Future<String>>();

		while (true)
		{

			// delay for 5 seconds
			Thread.sleep(5000L);
			
			/*********** Box migration tasks ***********/
			// looping through resource request
			List<MigrationBoxFile> bFiles = fRepository.findNextNewMigrationBoxFile();
			if (bFiles != null && bFiles.size() > 0)
			{
				// get right HttpContext object
				HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env, null, env.getProperty(Utils.ENV_PROPERTY_USERNAME));
				HttpContext httpContext = sessionAttributes != null ? (HttpContext) sessionAttributes.get("httpContext"):null;
				String sessionId = sessionAttributes != null ? (String) sessionAttributes.get("sessionId"):null;
				
				// process with the Box upload request
				for(MigrationBoxFile bFile : bFiles)
				{	
					if (futureBoxList.size() < threadNum)
					{
						futureBoxList.add( migrationTaskService.uploadBoxFile(bFile, httpContext, sessionId));
					}
				}
			}

		    // remove finished Box migration task from future list
			trimFutureListRemoveFinishedTask(futureBoxList);
			
			/*********** Google Groups migration tasks ***********/
			// looping through email request
			List<MigrationEmailMessage> messages = eRepository.getFirstNewMessagePerSite();
			// process with the message upload request
			for(MigrationEmailMessage message : messages)
			{	
				if (futureGoogleGroupList.size() < threadNum )
				{
					//call to microservice to upload message to Google Groups
					futureGoogleGroupList.add(migrationTaskService.uploadMessageToGoogleGroup(message));
				}
			}
			
		    // remove finished Google Groups migration task from future list
			trimFutureListRemoveFinishedTask(futureGoogleGroupList);
			
			/*********** update parent migration status for both Box and Google Groups migration request ***********/
			// if all itemized migration finishes, 
			// update the parent migration record for status and end time
			updateMigrationStatusAndEndTime();
		}
	}

	private void trimFutureListRemoveFinishedTask(
			List<Future<String>> futureList) {
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
				log.error("problem removing item from future list " + e.getMessage());
			}
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
			else if (Utils.MIGRATION_TYPE_GOOGLE_GROUP.equals(destination_type))
			{
				// for Google Groups email migration	
				updateMessageMigrationTimeAndStatus(mId);
			}
		}
	}

	/**
	 * if all box migration resource items within the migration is finished
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
			int itemStatusSuccessCount = 0;
			JSONArray itemsArray = new JSONArray();
			for(MigrationBoxFile mFile : mFileList)
			{
				String status = mFile.getStatus();
				// if there is error, status message won't have String "Box upload successful for file"
				if (status == null || status.indexOf("Box upload successful for file") == -1)
				{
					// increase the count for failed migrated item
					itemStatusFailureCount++;
					
					// report error 
					JSONObject itemJson = new JSONObject();
					itemJson.put(Utils.REPORT_ATTR_ITEM_ID, mFile.getTitle());
					itemJson.put(Utils.REPORT_ATTR_ITEM_STATUS, status);
					itemsArray.put(itemJson);
				}
				else
				{
					// increase the count for successfully migrated item
					itemStatusSuccessCount++;
				}
			}
			
			// the JSON object holds itemized status information
			JSONObject statusObject = new JSONObject();
			// migration type
			statusObject.put(Utils.REPORT_ATTR_TYPE, Utils.MIGRATION_TYPE_BOX);
			String statusSummary = Utils.REPORT_STATUS_OK;
			if (itemStatusSuccessCount == 0 && itemStatusFailureCount > 0)
			{
				// no item migrated successfully
				statusSummary = Utils.REPORT_STATUS_ERROR;
			}
			else if (itemStatusSuccessCount > 0 && itemStatusFailureCount > 0)
			{
				// with failures
				statusSummary = Utils.REPORT_STATUS_PARTIAL;
			}
			
			// count JSON
			JSONObject countsJson = new JSONObject();
			countsJson.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES, itemStatusSuccessCount);
			countsJson.put(Utils.REPORT_ATTR_COUNTS_ERRORS, itemStatusFailureCount);
	
			// add to top report level
			statusObject.put(Utils.REPORT_ATTR_COUNTS, countsJson);
			statusObject.put(Utils.REPORT_ATTR_ITEMS, itemsArray);
			
			statusObject.put(Utils.REPORT_ATTR_STATUS, statusSummary);
			//statusMap.put(Utils.MIGRATION_DATA, itemStatusList);

			// update the status of migration record
			mRepository.setMigrationStatus(statusObject.toString(), mId);
			
			// cleanup the added owner of admin user from CTools site
			String siteId = mRepository.getMigrationSiteId(mId);
			removeAddedAdminOwner(siteId);
		}
	}
	
	/**
	 * if all email migration items within the migration is finished
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
			Migration migration = mRepository.findOne(mId);
			String partialStatus = migration.getStatus();
			JSONObject status = new JSONObject(partialStatus);
			JSONArray messages = new JSONArray();
			int success,error,partial;
			success=error=partial=0;
			for (MigrationEmailMessage message: mMessageList) {
				JSONObject msgStatus = new JSONObject(message.getStatus());
				String itemStatus = (String)msgStatus.get(Utils.REPORT_ATTR_ITEM_STATUS);
				if(itemStatus.equals(Utils.REPORT_STATUS_OK)){
					success++;
				}
				if(itemStatus.equals(Utils.REPORT_STATUS_ERROR)){
					error++;
					messages.put(msgStatus);
				}
				if(itemStatus.equals(Utils.REPORT_STATUS_PARTIAL)){
					partial++;
					messages.put(msgStatus);
				}
			}
			status.put(Utils.REPORT_ATTR_ITEMS,messages);
			if(error>0){
				status.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_ERROR);
			}else if(success>0 & partial>0){
				status.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_PARTIAL);
			}else{
				status.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_OK);
			}
			JSONObject counts = Utils.getCountJsonObj();
			counts.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES,success);
			counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS,error);
			counts.put(Utils.REPORT_ATTR_COUNT_PARTIALS,partial);
			status.put(Utils.REPORT_ATTR_COUNTS,counts);
		
			// update the status of migration record
			mRepository.setMigrationStatus(status.toString(), mId);   
         // cleanup the added owner of admin user from CTools site
         	removeAddedAdminOwner(mId);
        }
	}

	/**
	 * // cleanup the added owner of admin user from CTools site
	 * @param migrationId
	 */
	protected void removeAddedAdminOwner(String siteId) {
		String adminUser = env.getProperty(Utils.ENV_PROPERTY_USERNAME);
		
		HashMap<String, Object> sessionAttributes = Utils.login_become_admin(env);
		
		if(sessionAttributes.isEmpty()){
			log.error("Logging into Ctools failed for the admin user.");
		}
		
		String adminSessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
		// the request string to add user to site with Owner role
		String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
				+ "direct/membership/" + adminUser + "::site:" + siteId + "?_sessionId=" + adminSessionId+ "&userIds=" + adminUser;
		
		HttpContext httpContext = (HttpContext) sessionAttributes.get("httpContext");
		HttpClient httpClient = HttpClientBuilder.create().build();
		try {
		    HttpDelete request = new HttpDelete(requestUrl);
		    request.setHeader("Content-Type", "application/x-www-form-urlencoded");
			HttpResponse response = httpClient.execute(request, httpContext);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 204) {
			    log.error(String.format("Failure to remove user \"%1$s\" from site %2$s ", adminUser, siteId ));
			    return;
			}
		} catch (IOException e) {
		    log.error(String.format("IOException Failure to remove user \"%1$s\" from site %2$s ", adminUser, siteId) + e);
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