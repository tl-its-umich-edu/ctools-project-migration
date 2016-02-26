package edu.umich.its.cpm;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.Callable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.concurrent.ListenableFuture;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.Metadata;
import com.box.sdk.ProgressListener;

@Service
@Component
public class MigrationTaskService {
	// String values used in content json feed
	private static final String COLLECTION_TYPE = "collection";
	private static final String CTOOLS_ACCESS_STRING = "/access/content";
	private static final String CTOOLS_CONTENT_STRING = "/content";
	private static final String CONTENT_JSON_ATTR_CONTENT_COLLECTION = "content_collection";
	private static final String CONTENT_JSON_ATTR_CONTAINER = "container";
	private static final String CONTENT_JSON_ATTR_TITLE = "title";
	private static final String CONTENT_JSON_ATTR_TYPE = "type";
	private static final String CONTENT_JSON_ATTR_URL = "url";
	private static final String CONTENT_JSON_ATTR_DESCRIPTION = "description";
	private static final String CONTENT_JSON_ATTR_AUTHOR = "author";
	private static final String CONTENT_JSON_ATTR_COPYRIGHT_ALERT = "copyrightAlert";
	private static final String CONTENT_JSON_ATTR_SIZE = "size";

	// true value used in entity feed
	private static final String BOOLEAN_TRUE = "true";

	// integer value of stream operation buffer size
	private static final int STREAM_BUFFER_CHAR_SIZE = 1024;

	private static final String LINE_BREAK = "\n";

	// Box has a hard limit of 5GB per any single file
	// use the decimal version of GB here, smaller than the binary version
	private static final long MAX_CONTENT_SIZE_FOR_BOX = 5L * 1024 * 1024 * 1024;

	private static final Logger log = LoggerFactory
			.getLogger(MigrationTaskService.class);

	/**
	 * Download CTools site resource in zip file
	 * 
	 * @return status of download
	 */
	public void downloadZippedFile(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId,
			HashMap<String, Object> sessionAttributes, String site_id,
			String migrationId, MigrationRepository repository) {
		// hold download status
		StringBuffer downloadStatus = new StringBuffer();
		List<MigrationFileItem> itemStatus = new ArrayList<MigrationFileItem>();

		// login to CTools and get sessionId
		if (sessionAttributes.containsKey("sessionId")) {
			String sessionId = (String) sessionAttributes.get("sessionId");
			HttpContext httpContext = (HttpContext) sessionAttributes
					.get("httpContext");

			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/content/site/" + site_id + ".json?_sessionId="
					+ sessionId;
			String siteResourceJson = null;
			try {
				siteResourceJson = restTemplate.getForObject(requestUrl,
						String.class);

				// null zip content
				byte[] zipContent = null;

				log.info(":downloadZippedFile begin: start downloading content zip file for site "
						+ site_id);
				//
				// Sends the response back to the user / browser. The
				// content for zip file type is "application/zip". We
				// also set the content disposition as attachment for
				// the browser to show a dialog that will let user
				// choose what action will he do to the sent content.
				//
				response.setContentType("application/zip");
				String zipFileName = site_id + "_content.zip";
				response.setHeader("Content-Disposition",
						"attachment;filename=\"" + zipFileName + "\"");

				ZipOutputStream out = new ZipOutputStream(
						response.getOutputStream());
				out.setLevel(9);

				// prepare zip entry for site content objects
				itemStatus = zipSiteContent(httpContext, siteResourceJson,
						sessionId, out);

				out.flush();
				out.close();
				log.info("Finished zip file download for site " + site_id);

			} catch (RestClientException e) {
				String errorMessage = "Cannot find site by siteId: " + site_id
						+ " " + e.getMessage();
				Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
						.type(MediaType.TEXT_PLAIN).build();
				log.error(errorMessage);
				downloadStatus.append(errorMessage);
			} catch (IOException e) {
				String errorMessage = "Problem getting content zip file for "
						+ site_id + " " + e.getMessage();
				Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(errorMessage).type(MediaType.TEXT_PLAIN)
						.build();
				log.error(errorMessage);
				downloadStatus.append(errorMessage);

			} catch (Exception e) {
				String errorMessage = "Migration status for " + site_id + " "
						+ e.getClass().getName();
				Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(errorMessage).type(MediaType.TEXT_PLAIN)
						.build();
				log.error("downloadZippedFile ", e);
				downloadStatus.append(errorMessage + LINE_BREAK);
			}
		} else {
			String userError = "Cannot become user " + userId;
			log.error(userError);
			downloadStatus.append(userError);
		}

		// the HashMap object holds itemized status information
		HashMap<String, Object> statusMap = new HashMap<String, Object>();
		statusMap.put(Utils.MIGRATION_STATUS, downloadStatus);
		statusMap.put(Utils.MIGRATION_DATA, itemStatus);
		JSONObject obj = new JSONObject(statusMap);

		// update the status and end_time of migration record
		repository
				.setMigrationEndTime(
						new java.sql.Timestamp(System.currentTimeMillis()),
						migrationId);
		repository.setMigrationStatus(obj.toString(), migrationId);

		return;

	}

	/**
	 * create zip entry for folders and files
	 */
	private List<MigrationFileItem> zipSiteContent(HttpContext httpContext,
			String siteResourceJson, String sessionId, ZipOutputStream out) {
		// the return list of MigrationFileItem objects, with migration status
		// recorded
		List<MigrationFileItem> fileItems = new ArrayList<MigrationFileItem>();

		// site root folder
		String rootFolderPath = null;

		JSONObject obj = new JSONObject(siteResourceJson);

		JSONArray array = obj
				.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

		for (int i = 0; i < array.length(); i++) {

			// item status information
			StringBuffer itemStatus = new StringBuffer();

			JSONObject contentItem = array.getJSONObject(i);

			String contentAccessUrl = contentItem
					.getString(CONTENT_JSON_ATTR_URL);
			// get only the url after "/access/" string
			String contentUrl = URLDecoder.decode(contentAccessUrl);
			contentUrl = contentUrl.substring(contentUrl
					.indexOf(CTOOLS_ACCESS_STRING)
					+ CTOOLS_ACCESS_STRING.length());

			// inside the JSON feed, the container string is of format
			// /content/<folder_url>
			// remote the prefix "/content"
			String container = URLDecoder.decode(Utils.getJSONString(
					contentItem, CONTENT_JSON_ATTR_CONTAINER));

			String type = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_TYPE);
			String title = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_TITLE);
			String copyrightAlert = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_COPYRIGHT_ALERT);

			// come checkpoints before migration
			itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
					title, copyrightAlert);

			if (itemStatus.length() == 0) {
				// no errors, proceed with migration
				if (COLLECTION_TYPE.equals(type)) {
					// folders
					if (rootFolderPath == null) {
						rootFolderPath = contentUrl;
					} else {
						// create the zipentry for the sub-folder first
						String folderName = contentUrl.replace(rootFolderPath,
								"");
						ZipEntry folderEntry = new ZipEntry(folderName);
						try {
							out.putNextEntry(folderEntry);
						} catch (IOException e) {
							String ioError = "zipSiteContent: problem closing zip entry "
									+ folderName + " " + e;
							log.error(ioError);
							itemStatus.append(ioError + LINE_BREAK);
						}
					}

				} else {
					// Call the zipFiles method for creating a zip stream.
					String zipFileStatus = zipFiles(type, httpContext, title,
							contentUrl, contentAccessUrl, sessionId, out);
					itemStatus.append(zipFileStatus + LINE_BREAK);
				}
			}
			MigrationFileItem fileItem = new MigrationFileItem(contentUrl,
					title, itemStatus.toString());

			fileItems.add(fileItem);
		} // for
		return fileItems;
	}

	/**
	 * create zip entry for files
	 */
	private String zipFiles(String type, HttpContext httpContext, String fileName,
			String fileUrl, String fileAccessUrl, String sessionId,
			ZipOutputStream out) {
		log.info("*** " + fileAccessUrl);
		
		// update file name
		fileName = modifyFileNameOnType(type, fileName);

		// record zip status
		StringBuffer zipFileStatus = new StringBuffer();

		// create httpclient
		HttpClient httpClient = HttpClientBuilder.create().build();
		InputStream content = null;
		try {
			// get file content from /access url
			HttpGet getRequest = new HttpGet(fileAccessUrl);
			getRequest.setHeader("Content-Type",
					"application/x-www-form-urlencoded");
			HttpResponse r = httpClient.execute(getRequest, httpContext);
			content = r.getEntity().getContent();
		} catch (Exception e) {
			log.info(e.getMessage());
		}

		// exit if content stream is null
		if (content == null)
			return null;

		try {
			int length = 0;
			byte data[] = new byte[STREAM_BUFFER_CHAR_SIZE];
			BufferedInputStream bContent = null;

			try {

				log.info("download file " + fileName);
				bContent = new BufferedInputStream(content);
				ZipEntry fileEntry = new ZipEntry(fileName);
				out.putNextEntry(fileEntry);
				int bCount = -1;
				if (Utils.CTOOLS_RESOURCE_TYPE_URL.equals(type))
				{
					out.write(getWebLinkContent(fileName, fileUrl).getBytes());
				}
				else
				{
					while ((bCount = bContent.read(data)) != -1) {
						out.write(data, 0, bCount);
						length = length + bCount;
					}
				}
				out.flush();

				try {
					out.closeEntry(); // The zip entry need to be closed
				} catch (IOException ioException) {
					String ioExceptionString = "zipFiles: problem closing zip entry "
							+ fileName + " " + ioException;
					log.error(ioExceptionString);
					zipFileStatus.append(ioExceptionString + LINE_BREAK);
				}
			} catch (IllegalArgumentException iException) {
				String IAExceptionString = "zipFiles: problem creating BufferedInputStream with content and length "
						+ data.length + iException;
				log.warn(IAExceptionString);
				zipFileStatus.append(IAExceptionString + LINE_BREAK);
			} finally {
				if (bContent != null) {
					try {
						bContent.close(); // The BufferedInputStream needs to be
											// closed
					} catch (IOException ioException) {
						String ioExceptionString = "zipFiles: problem closing FileChannel "
								+ ioException;
						log.warn(ioExceptionString);
						zipFileStatus.append(ioExceptionString + LINE_BREAK);
					}
				}
			}
		} catch (IOException e) {
			String ioExceptionString = " zipFiles--IOException: : fileName="
					+ fileName;
			log.warn(ioExceptionString);
			zipFileStatus.append(ioExceptionString + LINE_BREAK);
		} finally {
			if (content != null) {
				try {
					content.close(); // The input stream needs to be closed
				} catch (IOException ioException) {
					String ioExceptionString = "zipFiles: problem closing Inputstream content for"
							+ fileName + ioException;
					log.warn(ioExceptionString);
					zipFileStatus.append(ioExceptionString + LINE_BREAK);
				}
			}
			try {
				out.flush();
			} catch (Exception e) {
				log.warn(this + " zipFiles: exception " + e.getMessage());
			}
		}

		// return success message
		if (zipFileStatus.length() == 0) {
			zipFileStatus.append(fileName
					+ " was added into zip file successfully.");
		}

		return zipFileStatus.toString();
	}

	/**
	 * CTools Web Link content is exported as a html file, with the link inside
	 * @param fileName
	 * @param fileUrl
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private String getWebLinkContent(String fileName, String fileUrl)
	{
		// special handling of CTools Web link resources
		// will create a HTML file containing a link inside
		// For example: original fileUrl of format "/group/<site id>/http:__google.com.URL"
		// and the end result url will be "http://google.com"
		String urlContent = "";
		if (fileUrl.endsWith(Utils.CTOOLS_RESOURCE_TYPE_URL_EXTENSION))
		{
			// remote the 
			urlContent = fileUrl.substring(0, fileUrl.length()-4);
		}
		// get the last 
		urlContent = urlContent.substring(urlContent.lastIndexOf("/") + 1);
		// decode first
		try
		{
			urlContent = URLDecoder.decode(urlContent, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			log.error(this + " getWebLinkContent: UnsupportedEncodingException " + e);
		}
		// then replace all "_" char with "/", was encoded by CTools
		urlContent=urlContent.replace("_", "/");
		StringBuffer b = new StringBuffer();
		b.append("<a href=\"");
		b.append(urlContent);
		b.append("\">" + fileName + "</a>");
		return b.toString();
	}

	/*************** Box Migration ********************/
	@Async
	public Future<HashMap<String, String>> uploadToBox(Environment env,
			HttpServletRequest request, HttpServletResponse response,
			String userId, HashMap<String, Object> sessionAttributes,
			String siteId, String boxFolderId, String migrationId,
			MigrationRepository repository) throws InterruptedException {
		// the HashMap object to be returned
		HashMap<String, String> rvMap = new HashMap<String, String>();
		rvMap.put("userId", userId);
		rvMap.put("siteId", siteId);
		rvMap.put("migrationId", migrationId);
		
		StringBuffer boxMigrationStatus = new StringBuffer();
		List<MigrationFileItem> itemMigrationStatus = new ArrayList<MigrationFileItem>();

		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);
		String boxClientRedirectUrl = env
				.getProperty(Utils.BOX_CLIENT_REDIRECT_URL);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null) {
			String boxClientIdError = "Missing Box integration parameters (Box client id, client secret)";
			log.error(boxClientIdError);
			boxMigrationStatus.append(boxClientIdError + LINE_BREAK);
		}

		String remoteUserEmail = Utils.getUserEmail(userId);

		if (BoxUtils.getBoxAccessToken(userId) == null) {
			// go to Box authentication screen
			// get access token and refresh token and store locally
			BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUrl,
					remoteUserEmail, response);

			rvMap.put("status", "fail");
			return new AsyncResult<HashMap<String, String>>(rvMap);
		}

		if (siteId == null || boxFolderId == null) {
			String boxFolderIdError = "Missing params for CTools site id, or target Box folder id.";
			log.error(boxFolderIdError);
			boxMigrationStatus.append(boxFolderIdError + LINE_BREAK);
		}

		// get sessionId
		if (sessionAttributes.containsKey("sessionId")) {
			String sessionId = (String) sessionAttributes.get("sessionId");
			HttpContext httpContext = (HttpContext) sessionAttributes
					.get("httpContext");

			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/content/site/" + siteId + ".json?_sessionId="
					+ sessionId;
			String siteResourceJson = null;
			try {
				siteResourceJson = restTemplate.getForObject(requestUrl,
						String.class);
				itemMigrationStatus = boxUploadSiteContent(httpContext, userId,
						sessionId, boxClientId, boxClientSecret,
						siteResourceJson, boxFolderId);

			} catch (RestClientException e) {
				String errorMessage = "Cannot find site by siteId: " + siteId
						+ " " + e.getMessage();
				Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
						.type(MediaType.TEXT_PLAIN).build();
				log.error(errorMessage);
				boxMigrationStatus.append(errorMessage + LINE_BREAK);
			} catch (Exception e) {
				String errorMessage = "Migration status for " + siteId + " "
						+ e.getClass().getName();
				Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(errorMessage).type(MediaType.TEXT_PLAIN)
						.build();

				log.error("uploadToBox ", e);
				boxMigrationStatus.append(errorMessage + LINE_BREAK);
			}

			String uploadFinished = "Finished upload site content for site "
					+ siteId;
			log.info(uploadFinished);
			boxMigrationStatus.append(uploadFinished + LINE_BREAK);
		} else {
			String errorBecomeUser = "Problem become user to " + userId;
			log.error(errorBecomeUser);
			boxMigrationStatus.append(errorBecomeUser + LINE_BREAK);
		}

		// the HashMap object holds itemized status information
		HashMap<String, Object> statusMap = new HashMap<String, Object>();
		statusMap.put(Utils.MIGRATION_STATUS, boxMigrationStatus.toString());
		statusMap.put(Utils.MIGRATION_DATA, itemMigrationStatus);
		JSONObject obj = new JSONObject(statusMap);

		// update the status and end_time of migration record
		repository
				.setMigrationEndTime(
						new java.sql.Timestamp(System.currentTimeMillis()),
						migrationId);
		repository.setMigrationStatus(obj.toString(), migrationId);

		rvMap.put("status", "success");
		return new AsyncResult<HashMap<String, String>>(rvMap);
	}

	/**
	 * iterating though content json and upload folders and files to Box
	 */
	private List<MigrationFileItem> boxUploadSiteContent(
			HttpContext httpContext, String userId, String sessionId,
			String boxClientId, String boxClientSecret,
			String siteResourceJson, String boxFolderId) {

		List<MigrationFileItem> rv = new ArrayList<MigrationFileItem>();

		BoxAPIConnection api = new BoxAPIConnection(boxClientId,
				boxClientSecret, BoxUtils.getBoxAccessToken(userId),
				BoxUtils.getBoxRefreshToken(userId));
		
		// site root folder
		String rootFolderPath = null;

		JSONObject obj = new JSONObject(siteResourceJson);

		JSONArray array = obj
				.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

		// start a stack object, with element of site folder ids
		// the top of the stack is the current container folder
		// since the CTools site content json feed is depth-first search,
		// we can use the stack to store the current folder id,
		// and do pop() when moving to a different folder
		java.util.Stack<String> containerStack = new java.util.Stack<String>();
		// this is the parallel stack which stored the Box folder of those
		// container collections
		java.util.Stack<String> boxFolderIdStack = new java.util.Stack<String>();

		for (int i = 0; i < array.length(); i++) {
			// error flag
			boolean error_flag = false;

			// status for each item
			StringBuffer itemStatus = new StringBuffer();

			JSONObject contentItem = array.getJSONObject(i);

			// get only the url after "/access/content" string
			String contentAccessUrl = contentItem
					.getString(CONTENT_JSON_ATTR_URL);
			String contentUrl = URLDecoder.decode(contentAccessUrl);
			contentUrl = contentUrl.substring(contentUrl
					.indexOf(CTOOLS_ACCESS_STRING)
					+ CTOOLS_ACCESS_STRING.length());

			// inside the JSON feed, the container string is of format
			// /content/<folder_url>
			// remote the prefix "/content"
			String container = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_CONTAINER));
			container = container.substring(container
					.indexOf(CTOOLS_CONTENT_STRING)
					+ CTOOLS_CONTENT_STRING.length());

			String type = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_TYPE);
			String title = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_TITLE);
			String description = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_DESCRIPTION);
			// metadata
			String author = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_AUTHOR);
			String copyrightAlert = Utils.getJSONString(contentItem,
					CONTENT_JSON_ATTR_COPYRIGHT_ALERT);

			// come checkpoints before migration
			itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
					title, copyrightAlert);

			log.info("type=" + type + " error=" + itemStatus.toString());

			if (itemStatus.length() == 0) {
				// now alerts, do Box uploads next
				if (rootFolderPath == null && COLLECTION_TYPE.equals(type)) {
					// root folder
					rootFolderPath = contentUrl;

					// insert into stack
					containerStack.push(contentUrl);
					boxFolderIdStack.push(boxFolderId);
				}
				else
				{
					// do uploads
					HashMap<String, Object> rvValues= processBoxUploadSiteContent(userId, type, rootFolderPath,
							contentUrl, containerStack, boxFolderIdStack, title,
							container, boxFolderId, api, itemStatus, description,
							contentItem, httpContext, contentAccessUrl, author,
							copyrightAlert, sessionId);
					itemStatus = (StringBuffer) rvValues.get("itemStatus");
					containerStack = (java.util.Stack<String>) rvValues.get("containerStack");
					boxFolderIdStack = (java.util.Stack<String>) rvValues.get("boxFolderIdStack");
					log.debug("containerStack length=" + containerStack.size());
					log.debug("boxFolderStack length=" + boxFolderIdStack.size());
				}

			}

			// the status of file upload to Box
			MigrationFileItem item = new MigrationFileItem(contentUrl, title,
					itemStatus.toString());
			rv.add(item);
		} // for

		return rv;
	}

	/**
	 * perform couple of checks before migration starts
	 * 
	 * @param itemStatus
	 * @param contentUrl
	 * @param container
	 * @param title
	 * @param copyrightAlert
	 */
	private StringBuffer preMigrationChecks(StringBuffer itemStatus,
			String contentUrl, String container, String title,
			String copyrightAlert) {
		if (BOOLEAN_TRUE.equals(copyrightAlert)) {
			// do not migrate if the item is with copyright
			itemStatus.append(title
					+ " is not migrated because of copyright alert. "
					+ LINE_BREAK);
		} else if (contentUrl == null || contentUrl.length() == 0) {
			// log error if the content url is missing
			String urlError = "No url for content " + title;
			log.error(urlError);
			itemStatus.append(urlError + LINE_BREAK);
		} else if (container == null || container.length() == 0) {
			// log error if the content url is missing
			String containerError = "No container folder url for content "
					+ title;
			log.error(containerError);
			itemStatus.append(containerError + LINE_BREAK);
		}
		return itemStatus;
	}

	/**
	 * process Box upload request
	 * 
	 * @param type
	 * @param rootFolderPath
	 * @param contentUrl
	 * @param containerStack
	 * @param boxFolderIdStack
	 * @param title
	 * @param container
	 * @param boxFolderId
	 * @param api
	 * @param itemStatus
	 * @param description
	 * @param contentItem
	 * @param httpContext
	 * @param contentAccessUrl
	 * @param author
	 * @param copyrightAlert
	 * @param sessionId
	 * @return
	 */
	private HashMap<String, Object> processBoxUploadSiteContent(String userId, String type,
			String rootFolderPath, String contentUrl,
			java.util.Stack<String> containerStack,
			java.util.Stack<String> boxFolderIdStack, String title,
			String container, String boxFolderId, BoxAPIConnection api,
			StringBuffer itemStatus, String description,
			JSONObject contentItem, HttpContext httpContext,
			String contentAccessUrl, String author, String copyrightAlert,
			String sessionId) {

		if (COLLECTION_TYPE.equals(type)) {
			// folders

			log.info("Begin to create folder " + title);

			// pop the stack till the container equals to stack top
			while (!containerStack.empty()
					&& !container.equals(containerStack.peek())) {
				// sync pops
				containerStack.pop();
				boxFolderIdStack.pop();
			}

			// create box folder
			api = BoxUtils.refreshAccessAndRefreshTokens(userId, api);
			BoxFolder parentFolder = new BoxFolder(api, boxFolderIdStack.peek());
			try {
				BoxFolder.Info childFolderInfo = parentFolder
						.createFolder(title);
				itemStatus.append("folder " + title + " created.");

				// push the current folder id into the stack
				containerStack.push(contentUrl);
				boxFolderIdStack.push(childFolderInfo.getID());
				log.debug("top of stack folder id = " + containerStack.peek()
						+ " " + " container folder id=" + container);

				// get the BoxFolder object, get BoxFolder.Info object,
				// set description, and commit change
				BoxFolder childFolder = childFolderInfo.getResource();
				childFolderInfo.setDescription(description);
				childFolder.updateInfo(childFolderInfo);

			} catch (BoxAPIException e) {
				if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
					// 409 means name conflict - item already existed
					itemStatus.append("There is already a folder with name "
							+ title);

					String exisingFolderId = getExistingBoxFolderIdFromBoxException(
							e, title);
					if (exisingFolderId != null) {
						// push the current folder id into the stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push(exisingFolderId);
						log.debug("top of stack folder id = "
								+ containerStack.peek() + " "
								+ " container folder id=" + container);
					} else {
						log.info("Cannot find conflicting Box folder id for folder name "
								+ title);
					}
				}
			}
		} else {
			// files
			String fileName = contentUrl.replace(rootFolderPath, "");
			int size = Utils.getJSONInt(contentItem, CONTENT_JSON_ATTR_SIZE);

			// check whether the file size exceeds Box's limit
			if (size >= MAX_CONTENT_SIZE_FOR_BOX) {
				// stop upload this file
				itemStatus.append(title + " is of size " + size
						+ ", too big to be uploaded to Box" + LINE_BREAK);
			} else {
				// Call the uploadFile method to upload file to Box.
				//
				log.debug("file stack peek= " + containerStack.peek() + " "
						+ " container=" + container);

				while (!containerStack.empty()
						&& !container.equals(containerStack.peek())) {
					// sync pops
					containerStack.pop();
					boxFolderIdStack.pop();
				}

				if (boxFolderIdStack.empty()) {
					String parentError = "Cannot find parent folder for file "
							+ contentUrl;
					log.error(parentError);
				} else {
					itemStatus.append(uploadFile(userId, type, httpContext,
							boxFolderIdStack.peek(), fileName, contentUrl,
							contentAccessUrl, description, author,
							copyrightAlert, sessionId, api, size));
				}
			}
		}
		
		// returning all changed variables
		HashMap<String, Object> rv = new HashMap<String, Object>();
		rv.put("itemStatus", itemStatus);
		rv.put("containerStack", containerStack);
		rv.put("boxFolderIdStack", boxFolderIdStack);
		return rv;
	}

	/**
	 * upload files to Box
	 */
	private String uploadFile(String userId, String type, HttpContext httpContext, String boxFolderId,
			String fileName, String fileUrl, String fileAccessUrl,
			String fileDescription, String fileAuthor,
			String fileCopyrightAlert, String sessionId, BoxAPIConnection api, final long fileSize) {
		// status string
		StringBuffer status = new StringBuffer();

		log.info("begin to upload file " + fileUrl + " to box folder "
				+ boxFolderId);

		log.info("*** " + fileAccessUrl);

		// record zip status
		StringBuffer zipFileStatus = new StringBuffer();

		// create httpclient
		HttpClient httpClient = HttpClientBuilder.create().build();
		InputStream content = null;
		
		// update file name
		fileName = modifyFileNameOnType(type, fileName);
		
		if (Utils.CTOOLS_RESOURCE_TYPE_URL.equals(type))
		{
			// special handling of Web Links resources
			String webLinkContent = getWebLinkContent(fileName, fileUrl);
			content = new ByteArrayInputStream(webLinkContent.getBytes());
		}
		else
		{
			try {
				// get file content from /access url
				HttpGet getRequest = new HttpGet(fileAccessUrl);
				getRequest.setHeader("Content-Type",
						"application/x-www-form-urlencoded");
				HttpResponse r = httpClient.execute(getRequest, httpContext);
				content = r.getEntity().getContent();
			} catch (Exception e) {
				log.info(e.getMessage());
			}
		}

		// exit if content stream is null
		if (content == null)
			return null;

		int length = 0;
		byte data[] = new byte[STREAM_BUFFER_CHAR_SIZE];
		BufferedInputStream bContent = null;
		try {

			bContent = new BufferedInputStream(content);
			// check if Box access token needs refresh
			api = BoxUtils.refreshAccessAndRefreshTokens(userId, api);
			BoxFolder folder = new BoxFolder(api, boxFolderId);
			final String uploadFileName = fileName;
			BoxFile.Info newFileInfo = folder.uploadFile(bContent, fileName,
					STREAM_BUFFER_CHAR_SIZE, new ProgressListener() {
						public void onProgressChanged(long numBytes,
								long totalBytes) {
							log.debug(numBytes + " out of total bytes " + totalBytes + " and file size " + fileSize);
						}
					});

			// get the BoxFile object, get BoxFile.Info object, set description,
			// and commit change
			BoxFile newFile = newFileInfo.getResource();
			newFileInfo.setDescription(fileDescription);
			newFile.updateInfo(newFileInfo);

			// assign meta data
			Metadata metaData = new Metadata();
			metaData.add("/copyrightAlert",
					fileCopyrightAlert == null ? "false" : "true");
			metaData.add("/author", fileAuthor);
			newFile.createMetadata(metaData);

			log.info("upload success for file " + fileUrl);
		} catch (BoxAPIException e) {
			if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
				// 409 means name conflict - item already existed
				String conflictString = "There is already a file with name "
						+ fileName;
				log.info(conflictString);
				status.append(conflictString + LINE_BREAK);
			}
		} catch (IllegalArgumentException iException) {
			String ilExceptionString = "problem creating BufferedInputStream for file "
					+ fileName
					+ " with content and length "
					+ data.length
					+ iException;
			log.warn(ilExceptionString);
			status.append(ilExceptionString + LINE_BREAK);
		} catch (Exception e) {
			String ilExceptionString = "problem creating BufferedInputStream for file "
					+ fileName
					+ " with content and length "
					+ data.length
					+ e;
			log.warn(ilExceptionString);
			status.append(ilExceptionString + LINE_BREAK);
		} finally {
			if (bContent != null) {
				try {
					bContent.close(); // The BufferedInputStream needs to be
										// closed
				} catch (IOException ioException) {
					String ioExceptionString = "problem closing FileChannel for file "
							+ fileName + " " + ioException;
					log.error(ioExceptionString);
					status.append(ioExceptionString + LINE_BREAK);
				}
			}
		}
		if (content != null) {
			try {
				content.close(); // The input stream needs to be closed
			} catch (IOException ioException) {
				String ioExceptionString = "zipFiles: problem closing Inputstream content for file "
						+ fileName + " " + ioException;
				log.error(ioExceptionString);
				status.append(ioExceptionString + LINE_BREAK);
			}
		}

		// box upload success
		if (status.length() == 0) {
			status.append("Box upload successful for file " + fileName + ".");
		}
		return status.toString();
	}

	/**
	 * for Web Link and citation type of resources, append ".html" to the file name String
	 * @param type
	 * @param fileName
	 * @return
	 */
	private String modifyFileNameOnType(String type, String fileName) {
		if (Utils.CTOOLS_RESOURCE_TYPE_CITATION.equals(type) || Utils.CTOOLS_RESOURCE_TYPE_URL.equals(type))
		{
			fileName = fileName + Utils.HTML_FILE_EXTENSION;
		}
		return fileName;
	}

	/**
	 * Based on the JSON returned inside BoxAPIException object, find out the id
	 * of conflicting box folder
	 * 
	 * @return id
	 */
	private String getExistingBoxFolderIdFromBoxException(BoxAPIException e,
			String folderTitle) {
		String existingFolderId = null;
		// here is the example JSON returned
		// {
		// "type":"error",
		// "status":409,
		// "code":"item_name_in_use",
		// "context_info":{
		// "conflicts":[
		// {
		// "type":"folder",
		// "id":"5443268429",
		// "sequence_id":"0",
		// "etag":"0",
		// "name":"folder1"
		// }
		// ]
		// },
		// "help_url":"http:\/\/developers.box.com\/docs\/#errors",
		// "message":"Item with the same name already exists",
		// "request_id":"153175908556537d483098d"
		// }
		if (e.getResponse() == null)
			return null;
		JSONObject boxException = new JSONObject(e.getResponse());
		if (boxException == null)
			return null;
		JSONObject context_info = boxException.getJSONObject("context_info");
		if (context_info == null)
			return null;
		JSONArray conflicts = context_info.getJSONArray("conflicts");
		if (conflicts == null)
			return null;

		for (int index = 0; index < conflicts.length(); index++) {
			JSONObject conflict = conflicts.getJSONObject(index);
			String conflictType = conflict.getString("type");
			if (conflictType != null && conflictType.equals("folder")) {
				String folderId = conflict.getString("id");
				String folderName = conflict.getString("name");
				if (folderName != null && folderName.equals(folderTitle)) {
					// found the existing folder id, break
					existingFolderId = folderId;
					break;
				}
			}
		}
		return existingFolderId;
	}

}