package edu.umich.its.cpm;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.Metadata;
import com.box.sdk.ProgressListener;

@Service
@Component
public
class MigrationTaskService {

	@Autowired
	BoxAuthUserRepository uRepository;

	@Autowired
	MigrationBoxFileRepository fRepository;

	@Autowired
	MigrationEmailMessageRepository mRepository;

	@Autowired
	private Environment env;

	// String values used in content json feed
	private static final String CTOOLS_ACCESS_STRING = "/access/content";
	private static final String CTOOLS_CITATION_ACCESS_STRING = "/access/citation/content";
	private static final String CTOOLS_CONTENT_STRING = "/content";
	private static final String CONTENT_JSON_ATTR_CONTENT_COLLECTION = "content_collection";
	private static final String CONTENT_JSON_ATTR_CONTAINER = "container";
	private static final String CONTENT_JSON_ATTR_TITLE = "title";
	private static final String CONTENT_JSON_ATTR_TYPE = "type";
	private static final String CONTENT_JSON_ATTR_URL = "url";
	private static final String CONTENT_JSON_ATTR_DESCRIPTION = "description";
	private static final String CONTENT_JSON_ATTR_AUTHOR = "author";
	private static final String CONTENT_JSON_ATTR_COPYRIGHT_ALERT = "copyrightAlert";
	private static final String CONTENT_JSON_ATTR_WEB_LINK_URL = "webLinkUrl";
	private static final String CONTENT_JSON_ATTR_SIZE = "size";

	// true value used in entity feed
	private static final String BOOLEAN_TRUE = "true";

	// integer value of stream operation buffer size
	private static final int STREAM_BUFFER_CHAR_SIZE = 1024;

	// Box has a hard limit of 5GB per any single file
	// use the decimal version of GB here, smaller than the binary version
	private static final long MAX_CONTENT_SIZE_FOR_BOX = 5L * 1024 * 1024 * 1024;

	// TODO: map the ctools to google roles
	private static final HashMap<String,String> MemberRoleMap = new HashMap<String,String>() {
		private static final long serialVersionUID = -8839396373117387837L;
		{
			put(Utils.ROLE_OWNER,"OWNER");
			put(Utils.ROLE_ORGANIZER,"MANAGER");	
			put(Utils.ROLE_MEMBER,"MEMBER");
			put(Utils.ROLE_OBSERVER,"MEMBER");
			put(Utils.ROLE_MAINTAINER,"OWNER");
			put(Utils.ROLE_INSTRUCTOR,"MANAGER");
			put(Utils.ROLE_STUDENT,"MEMBER");
			// Role that will be used if nothing else matches
			put("DEFAULT","MEMBER");
		}};

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
			if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
				String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
				HttpContext httpContext = (HttpContext) sessionAttributes
						.get("httpContext");

				// 3. get all sites that user have permission site.upd
				RestTemplate restTemplate = new RestTemplate();
				// the url should be in the format of
				// "https://server/direct/site/SITE_ID.json"
				String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
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
					response.setContentType(Utils.MIME_TYPE_ZIP);
					String zipFileName = site_id + "_content.zip";
					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					ZipOutputStream out = new ZipOutputStream(
							response.getOutputStream());
					String compressionLevel = env.getProperty(Utils.ENV_ZIP_COMPRESSSION_LEVEL);
					if ((compressionLevel == null) || (compressionLevel.isEmpty())) {
						// set compression level to high by default
						out.setLevel(9);
						log.error("The property \"zip.compression.level\" is not set");
					} else {
						out.setLevel(Integer.parseInt(compressionLevel.trim()));
					}

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
					downloadStatus.append(errorMessage + Utils.LINE_BREAK);
				}
			} else {
				String userError = "Cannot become user " + userId;
				log.error(userError);
				downloadStatus.append(userError);
			}

			// the HashMap object holds itemized status information
			JSONObject statusJson = new JSONObject();
			statusJson.append(Utils.REPORT_ATTR_TYPE, Utils.REPORT_ATTR_TYPE_RESOURCE_ZIP);
			
			// count JSON
			JSONObject countsJson = new JSONObject();
			int errorCount = 0;
			int successItemCount = 0;
			// items json
			JSONArray itemsArray = new JSONArray();
			for(MigrationFileItem fItem : itemStatus)
			{
				String status = fItem.getStatus();
				if (status.isEmpty() || status.toLowerCase().contains("success"))
				{
					successItemCount++;
				}
				else
				{
					errorCount++;
					
					// report error 
					JSONObject itemJson = new JSONObject();
					itemJson.append(Utils.REPORT_ATTR_ITEM_ID, fItem.getFile_name());
					itemJson.append(Utils.REPORT_ATTR_ITEM_STATUS, fItem.getStatus());
					itemsArray.put(itemJson);
				}
			}
			countsJson.append(Utils.REPORT_ATTR_COUNTS_SUCCESSES, successItemCount);
			countsJson.append(Utils.REPORT_ATTR_COUNTS_ERRORS, errorCount);
			
			// add to top report level
			statusJson.append(Utils.REPORT_ATTR_COUNTS, countsJson);
			statusJson.append(Utils.REPORT_ATTR_ITEMS, itemsArray);
			

			statusJson.append(Utils.MIGRATION_STATUS, errorCount == 0 ? Utils.REPORT_STATUS_OK:Utils.REPORT_STATUS_PARTIAL);

			// update the status and end_time of migration record
			setMigrationEndTimeAndStatus(migrationId, repository, statusJson);
			

			return;

		}

		/**
		 * update the status and end_time of migration record
		 * @param migrationId
		 * @param repository
		 * @param obj
		 */
		public void setMigrationEndTimeAndStatus(String migrationId,
				MigrationRepository repository, JSONObject obj) {
			repository.setMigrationEndTime(
					new java.sql.Timestamp(System.currentTimeMillis()),
					migrationId);
			repository.setMigrationStatus(obj.toString(), migrationId);
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

			// the map stores folder name conversions;
			// folder name can be changed within CTools:
			// it can be named differently, while the old name still uses in the
			// folder/resource ids
			HashMap<String, String> folderNameMap = new HashMap<String, String>();
			for (int i = 0; i < array.length(); i++) {

				// item status information
				StringBuffer itemStatus = new StringBuffer();

				JSONObject contentItem = array.getJSONObject(i);

				String type = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TYPE);
				String title = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TITLE);
				String copyrightAlert = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_COPYRIGHT_ALERT);

				String contentAccessUrl = contentItem
						.getString(CONTENT_JSON_ATTR_URL);

				// get only the url after "/access/" string
				String contentUrl = getContentUrl(contentAccessUrl);
				if (contentUrl == null) {
					// log error
					itemStatus.append("Content url " + contentUrl
							+ " does not contain " + CTOOLS_ACCESS_STRING + " nor "
							+ CTOOLS_CITATION_ACCESS_STRING);

					// document the error and break
					MigrationFileItem item = new MigrationFileItem(contentUrl,
							title, itemStatus.toString());
					fileItems.add(item);
					break;
				}

				// modify the contentAccessUrl if needed for copyright alert setting
				// always export the resource content regardless of the copyright
				// settings
				contentAccessUrl = Utils.getCopyrightAcceptUrl(copyrightAlert,
						contentAccessUrl);

				// get container string from content url
				String container = getContainerStringFromContentUrl(contentUrl);

				// come checkpoints before migration
				itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
						title);

				if (itemStatus.length() == 0) {
					// no errors, proceed with migration
					if (Utils.COLLECTION_TYPE.equals(type)) {
						// folders
						if (rootFolderPath == null) {
							rootFolderPath = contentUrl;
						} else {
							// create the zipentry for the sub-folder first
							String folderName = contentUrl.replace(rootFolderPath,
									"");
							// update folder name
							folderNameMap = Utils.updateFolderNameMap(
									folderNameMap, title, folderName);
							if (folderNameMap.containsKey(folderName)) {
								// if the folder name have / in it then we are not zipping the file with original name instead the folder
								// name will contain _ in it. As having the / will have cause the zip library creating inner folders
								if (!(StringUtils.countOccurrencesOf(folderNameMap.get(folderName), "/") > 1)) {
									folderName = folderNameMap.get(folderName);
								}
							}

							log.info("download folder " + folderName);

							ZipEntry folderEntry = new ZipEntry(folderName);
							try {
								out.putNextEntry(folderEntry);
							} catch (IOException e) {
								String ioError = "zipSiteContent: problem closing zip entry "
										+ folderName + " " + e;
								log.error(ioError);
								itemStatus.append(ioError + Utils.LINE_BREAK);
							}
						}

					} else {
						// get the zip file name with folder path info
						String zipFileName = container.substring(container
								.indexOf(rootFolderPath) + rootFolderPath.length());
						zipFileName = zipFileName.concat(Utils.sanitizeName(type,
								title));
						log.info("zip download processing file " + zipFileName);

						// value not null for WebLink content item
						String webLinkUrl = Utils.getJSONString(contentItem,
								CONTENT_JSON_ATTR_WEB_LINK_URL);

						// Call the zipFiles method for creating a zip stream.
						String zipFileStatus = zipFiles(type, httpContext,
								zipFileName, title, webLinkUrl, contentAccessUrl,
								sessionId, out, folderNameMap);
						itemStatus.append(zipFileStatus);
					}
				}
				
				MigrationFileItem fileItem = new MigrationFileItem(contentUrl,
						title, itemStatus.toString());

				fileItems.add(fileItem);
			} // for
			return fileItems;
		}

		/**
		 * based on the content url, get its parent folder - container folder url
		 * ends with "/", need remove the ending "/" first;
		 * <container_path_end_with_slash><content_title>
		 *
		 * @param contentUrl
		 * @return
		 */
		private String getContainerStringFromContentUrl(String contentUrl) {
			String container = contentUrl.endsWith(Utils.PATH_SEPARATOR) ? contentUrl
					.substring(0, contentUrl.length() - 1) : contentUrl;
					container = container.substring(0,
							container.lastIndexOf(Utils.PATH_SEPARATOR) + 1);
					return container;
		}

		/**
		 * create zip entry for files
		 */
		private String zipFiles(String type, HttpContext httpContext,
				String fileName, String title, String webLinkUrl,
				String fileAccessUrl, String sessionId, ZipOutputStream out,
				HashMap<String, String> folderNameUpdates) {
			log.info("*** " + fileAccessUrl);

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
					bContent = new BufferedInputStream(content);

					// checks for folder renames
					fileName = Utils.updateFolderPathForFileName(fileName,
							folderNameUpdates);

					log.info("download file " + fileName);

					if (Utils.isOfURLMIMEType(type)) {
						try {
							// get the html file content first
							String webLinkContent = Utils.getWebLinkContent(title,
									webLinkUrl);

							ZipEntry fileEntry = new ZipEntry(fileName);
							out.putNextEntry(fileEntry);
							out.write(webLinkContent.getBytes());
						} catch (java.net.MalformedURLException e) {
							// return status with error message
							zipFileStatus
							.append(e.getMessage() + "Link "
									+ title
									+ " could not be migrated. Please change the link name to be the complete URL and migrate the site again.");
						} catch (IOException e) {
							// return status with error message
							zipFileStatus
							.append(e.getMessage() + "Link "
									+ title
									+ " could not be migrated. Please change the link name to be the complete URL and migrate the site again.");
						}
					} else {

						ZipEntry fileEntry = new ZipEntry(fileName);
						out.putNextEntry(fileEntry);
						int bCount = -1;

						bContent = new BufferedInputStream(content);
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
						zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
					}
				} catch (IllegalArgumentException iException) {
					String IAExceptionString = "zipFiles: problem creating BufferedInputStream with content and length "
							+ data.length + iException;
					log.warn(IAExceptionString);
					zipFileStatus.append(IAExceptionString + Utils.LINE_BREAK);
				} finally {
					if (bContent != null) {
						try {
							bContent.close(); // The BufferedInputStream needs to be
							// closed
						} catch (IOException ioException) {
							String ioExceptionString = "zipFiles: problem closing FileChannel "
									+ ioException;
							log.warn(ioExceptionString);
							zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
						}
					}
				}
			} catch (IOException e) {
				String ioExceptionString = " zipFiles--IOException: : fileName="
						+ fileName;
				log.warn(ioExceptionString);
				zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
			} finally {
				if (content != null) {
					try {
						content.close(); // The input stream needs to be closed
					} catch (IOException ioException) {
						String ioExceptionString = "zipFiles: problem closing Inputstream content for"
								+ fileName + ioException;
						log.warn(ioExceptionString);
						zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
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

		/*************** Box Migration ********************/
		@Async
		private Future<HashMap<String, String>> uploadToBox(String userId, HashMap<String, Object> sessionAttributes,
				String siteId, String boxFolderId, String migrationId,
				MigrationRepository repository, BoxAuthUserRepository uRepository) throws InterruptedException {
			// the HashMap object to be returned
			HashMap<String, String> rvMap = new HashMap<String, String>();
			rvMap.put("userId", userId);
			rvMap.put("siteId", siteId);
			rvMap.put("migrationId", migrationId);

			StringBuffer boxMigrationStatus = new StringBuffer();
			List<MigrationFileItem> itemMigrationStatus = new ArrayList<MigrationFileItem>();

			// the HashMap object holds itemized status information
			HashMap<String, Object> statusMap = new HashMap<String, Object>();
			statusMap.put(Utils.MIGRATION_STATUS, boxMigrationStatus.toString());
			statusMap.put(Utils.MIGRATION_DATA, itemMigrationStatus);

			// update the status and end_time of migration record
			setMigrationEndTimeAndStatus(migrationId, repository, new JSONObject(statusMap));

			rvMap.put(Utils.MIGRATION_STATUS, Utils.STATUS_SUCCESS);
			return new AsyncResult<HashMap<String, String>>(rvMap);
		}

		/**
		 * iterating though content json and upload folders and files to Box
		 */
		public List<MigrationFileItem> boxUploadSiteContent(
				String migration_id, HttpContext httpContext, String userId, String sessionId,
				String siteResourceJson, String boxFolderId) {

			List<MigrationFileItem> rv = new ArrayList<MigrationFileItem>();

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

				// get only the url after "/access/content" string
				String contentAccessUrl = contentItem
						.getString(CONTENT_JSON_ATTR_URL);
				String contentUrl = getContentUrl(contentAccessUrl);
				if (contentUrl == null) {
					itemStatus.append("Content url " + contentUrl
							+ " does not contain " + CTOOLS_ACCESS_STRING + " nor "
							+ CTOOLS_CITATION_ACCESS_STRING);

					// document the error and break
					MigrationFileItem item = new MigrationFileItem(contentUrl,
							title, itemStatus.toString());
					rv.add(item);
					break;
				}

				// modify the contentAccessUrl if needed for copyright alert setting
				// always export the resource content regardless of the copyright
				// settings
				contentAccessUrl = Utils.getCopyrightAcceptUrl(copyrightAlert,
						contentAccessUrl);

				// get container string from content url
				String container = getContainerStringFromContentUrl(contentUrl);

				// come checkpoints before migration
				itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
						title);

				log.info("type=" + type + " contentUrl=" + contentUrl + " error="
						+ itemStatus.toString());

				if (itemStatus.length() == 0) {
					// now alerts, do Box uploads next
					if (rootFolderPath == null
							&& Utils.COLLECTION_TYPE.equals(type)) {
						// root folder
						rootFolderPath = contentUrl;

						// insert into stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push(boxFolderId);
					} else {
						// value not null only for Web Link Item
						String webLinkUrl = Utils.getJSONString(contentItem,
								CONTENT_JSON_ATTR_WEB_LINK_URL);
						try {
							// do uploads
							HashMap<String, Object> rvValues = processAddBoxFolders(
									migration_id,
									userId, type, rootFolderPath, contentUrl,
									containerStack, boxFolderIdStack, title,
									container, boxFolderId, itemStatus,
									description, contentItem, httpContext,
									webLinkUrl, contentAccessUrl, author,
									copyrightAlert, sessionId);
							itemStatus = (StringBuffer) rvValues.get("itemStatus");
							containerStack = (java.util.Stack<String>) rvValues
									.get("containerStack");
							boxFolderIdStack = (java.util.Stack<String>) rvValues
									.get("boxFolderIdStack");
							log.debug("containerStack length="
									+ containerStack.size());
							log.debug("boxFolderStack length="
									+ boxFolderIdStack.size());
						} catch (BoxAPIException e) {
							log.error(this + " boxUploadSiteContent "
									+ e.getResponse());
							JSONObject eJSON = new JSONObject(e.getResponse());
							String errorMessage = eJSON.has("context_info") ? eJSON
									.getString("context_info") : "";
									itemStatus
									.append("Box upload process was stopped due to the following error. Please rename the folder/resource item and migrate site again: \""
											+ errorMessage + "\"");
									// the status of file upload to Box
									MigrationFileItem item = new MigrationFileItem(
											contentUrl, title, itemStatus.toString());
									rv.add(item);

									// catch the BoxAPIException e
									// and halt the whole upload process
									break;
						}
					}

				}

				// exclude the root folder level in the status report
				if (i == 0)
					continue;

				// the status of file upload to Box
				MigrationFileItem item = new MigrationFileItem(contentUrl, title,
						itemStatus.toString());
				rv.add(item);
			} // for

			return rv;
		}


		/**
		 * get substring of contentAccessUrl
		 *
		 * @param itemStatus
		 * @param contentAccessUrl
		 * @return
		 */
		private String getContentUrl(String contentAccessUrl) {
			String contentUrl = URLDecoder.decode(contentAccessUrl);
			if (contentUrl.contains(CTOOLS_ACCESS_STRING)) {
				// non-citation resource
				contentUrl = contentUrl.substring(contentUrl
						.indexOf(CTOOLS_ACCESS_STRING)
						+ CTOOLS_ACCESS_STRING.length());
			} else if (contentUrl.contains(CTOOLS_CITATION_ACCESS_STRING)) {
				// citation resource
				contentUrl = contentUrl.substring(contentUrl
						.indexOf(CTOOLS_CITATION_ACCESS_STRING)
						+ CTOOLS_CITATION_ACCESS_STRING.length());
			} else {
				// log error
				contentUrl = null;
			}
			return contentUrl;
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
				String contentUrl, String container, String title) {
			if (contentUrl == null || contentUrl.length() == 0) {
				// log error if the content url is missing
				String urlError = "No url for content " + title;
				log.error(urlError);
				itemStatus.append(urlError + Utils.LINE_BREAK);
			} else if (container == null || container.length() == 0) {
				// log error if the content url is missing
				String containerError = "No container folder url for content "
						+ title;
				log.error(containerError);
				itemStatus.append(containerError + Utils.LINE_BREAK);
			}
			return itemStatus;
		}

		/**
		 * process content JSON file
		 * @param migrationId
		 * @param userId
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
		 * @param webLinkUrl
		 * @param contentAccessUrl
		 * @param author
		 * @param copyrightAlert
		 * @param sessionId
		 * @return
		 * @throws BoxAPIException
		 */
		private HashMap<String, Object> processAddBoxFolders(String migrationId, String userId,
				String type, String rootFolderPath, String contentUrl,
				java.util.Stack<String> containerStack,
				java.util.Stack<String> boxFolderIdStack, String title,
				String container, String boxFolderId,
				StringBuffer itemStatus, String description,
				JSONObject contentItem, HttpContext httpContext, String webLinkUrl,
				String contentAccessUrl, String author, String copyrightAlert,
				String sessionId) throws BoxAPIException {

			if (Utils.COLLECTION_TYPE.equals(type)) {
				// folders

				log.info("Begin to create folder " + title);

				// pop the stack till the container equals to stack top
				while (!containerStack.empty()
						&& !container.equals(containerStack.peek())) {
					// sync pops
					containerStack.pop();
					boxFolderIdStack.pop();
				}

				BoxAPIConnection api = BoxUtils.getBoxAPIConnection(userId, uRepository);
				if (api == null) {
					// exit if no Box API connection could be made
					// returning all changed variables
					HashMap<String, Object> rv = new HashMap<String, Object>();
					rv.put("itemStatus", "Cannot create Box folder for folder " + title);
					rv.put("containerStack", containerStack);
					rv.put("boxFolderIdStack", boxFolderIdStack);
					return rv;
				}

				// create box folder
				BoxFolder parentFolder = new BoxFolder(api, boxFolderIdStack.peek());
				String sanitizedTitle = Utils.sanitizeName(type, title);
				try {
					BoxFolder.Info childFolderInfo = parentFolder
							.createFolder(sanitizedTitle);
					itemStatus.append("folder " + sanitizedTitle + " created.");

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
								+ title + "- folder was not created in Box");

						String exisingFolderId = getExistingBoxFolderIdFromBoxException(
								e, sanitizedTitle);
						if (exisingFolderId != null) {
							// push the current folder id into the stack
							containerStack.push(contentUrl);
							boxFolderIdStack.push(exisingFolderId);
							log.error("top of stack folder id = "
									+ containerStack.peek() + " "
									+ " container folder id=" + container);
						} else {
							log.error("Cannot find conflicting Box folder id for folder name "
									+ sanitizedTitle);
						}
					} else {
						// log the exception message
						log.error(e.getResponse() + " for " + title);

						// and throws the exception,
						// so that the parent function can catch it and stop the
						// whole upload process
						throw e;
					}
				}
			} else {
				// files
				long size = Utils.getJSONLong(contentItem, CONTENT_JSON_ATTR_SIZE);

				// check whether the file size exceeds Box's limit
				if (size >= MAX_CONTENT_SIZE_FOR_BOX) {
					// stop upload this file
					itemStatus.append(title + " is of size " + size
							+ ", too big to be uploaded to Box" + Utils.LINE_BREAK);
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
						// insert records into database
						// ready for multi-thread processing
						log.info(" time to insert file record folder id=" + boxFolderIdStack.peek() );
						MigrationBoxFile mFile = new MigrationBoxFile(migrationId, userId, boxFolderIdStack.peek(),
								type, title, webLinkUrl,
								contentAccessUrl, description, author,
								copyrightAlert, size, null,
								null, null);
						fRepository.save(mFile);
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
		 * upload file to Box
		 * @param bFile
		 * @param httpContext
		 * @return
		 */
		@Async
		protected Future<String> uploadBoxFile(MigrationBoxFile bFile, HttpContext httpContext) {

			// get all bFile params
			String id = bFile.getId();
			String userId = bFile.getUser_id();
			String type = bFile.getType();
			String boxFolderId = bFile.getBox_folder_id();
			String fileName = bFile.getTitle();

			String webLinkUrl = bFile.getWeb_link_url();
			String fileAccessUrl = bFile.getFile_access_url();
			String fileDescription = bFile.getDescription();
			String fileAuthor = bFile.getAuthor();
			String fileCopyrightAlert = bFile.getCopyright_alert();
			final long fileSize = bFile.getFile_size();

			BoxAPIConnection api = BoxUtils.getBoxAPIConnection(userId, uRepository);

			// status string
			StringBuffer status = new StringBuffer();

			log.info("begin to upload file " + fileName + " to box folder "
					+ boxFolderId + " " + fileAccessUrl);

			// mark the file as being processed
			fRepository.setMigrationBoxFileStartTime(id, new Timestamp(System.currentTimeMillis()));

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

				if (Utils.isOfURLMIMEType(type)) {
					try {
						// special handling of Web Links resources
						content = new ByteArrayInputStream(Utils.getWebLinkContent(
								fileName, webLinkUrl).getBytes());
					} catch (java.net.MalformedURLException e) {
						// return status with error message
						status.append("Link "
								+ fileName
								+ " could not be migrated. Please change the link name to be the complete URL and migrate the site again.");
						return new AsyncResult<String>(status.toString());
					}
				}
			} catch (java.io.IOException e) {
				log.info(this + " uploadFile: cannot get web link contenet "
						+ e.getMessage());
			}

			// update file name
			fileName = Utils.modifyFileNameOnType(type, fileName);

			// exit if content stream is null
			if (content == null)
				return null;

			BufferedInputStream bContent = null;
			try {

				bContent = new BufferedInputStream(content);
				BoxFolder folder = new BoxFolder(api, boxFolderId);
				log.info("upload file size " + fileSize + " to folder " + folder.getID());

				BoxFile.Info newFileInfo = folder.uploadFile(bContent,
						Utils.sanitizeName(type, fileName),
						fileSize, new ProgressListener() {
					public void onProgressChanged(long numBytes,
							long totalBytes) {
						log.info(numBytes + " out of total bytes "
								+ totalBytes);
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

				log.info("upload success for file " + fileName);
			} catch (BoxAPIException e) {
				if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
					// 409 means name conflict - item already existed
					String conflictString = "There is already a file with name "
							+ fileName + " - file was not added to Box";
					log.info(conflictString);
					status.append(conflictString + Utils.LINE_BREAK);
				}
				log.error(this + "uploadFile fileName=" + fileName
						+ e.getResponse());
			} catch (IllegalArgumentException iException) {
				String ilExceptionString = "problem creating BufferedInputStream for file "
						+ fileName
						+ " with content and length "
						+ fileSize
						+ iException;
				log.warn(ilExceptionString);
				status.append(ilExceptionString + Utils.LINE_BREAK);
			} catch (Exception e) {
				String ilExceptionString = "problem creating BufferedInputStream for file "
						+ fileName + " with content and length " + fileSize + e;
				log.warn(ilExceptionString);
				status.append(ilExceptionString + Utils.LINE_BREAK);
			} finally {
				if (bContent != null) {
					try {
						bContent.close(); // The BufferedInputStream needs to be
						// closed
					} catch (IOException ioException) {
						String ioExceptionString = "problem closing FileChannel for file "
								+ fileName + " " + ioException;
						log.error(ioExceptionString);
						status.append(ioExceptionString + Utils.LINE_BREAK);
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
					status.append(ioExceptionString + Utils.LINE_BREAK);
				}
			}

			// box upload success
			if (status.length() == 0) {
				status.append("Box upload successful for file " + fileName + ".");
			}

			// update the status and end time for file item
			fRepository.setMigrationBoxFileEndTime(id, new java.sql.Timestamp(System.currentTimeMillis()));
			fRepository.setMigrationBoxFileStatus(id, status.toString());

			return new AsyncResult<String>(status.toString());
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


		/**************** MailArchive content ***********/
		/**
		 * Download MailArchive resource in zip file
		 * @param env
		 * @param request
		 * @param response
		 * @param userId
		 * @param sessionAttributes
		 * @param site_id
		 * @param migrationId
		 * @param repository
		 */
		public void downloadMailArchiveZipFile(Environment env, HttpServletRequest request,
				HttpServletResponse response, String userId,
				HashMap<String, Object> sessionAttributes, String site_id,
				String migrationId, MigrationRepository repository) {
			// hold download status

			Map<String, String[]> parameterMap = request.getParameterMap();
			String destination_type = parameterMap.get("destination_type")[0];
			JSONObject downloadStatus = migrationStatusObject(destination_type);
			// login to CTools and get sessionId
			if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
				String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
				HttpContext httpContext = (HttpContext) sessionAttributes
						.get("httpContext");
				try {
					//
					// Sends the response back to the user / browser. The
					// content for zip file type is "application/zip". We
					// also set the content disposition as attachment for
					// the browser to show a dialog that will let user
					// choose what action will he do to the sent content.
					//
					response.setContentType(Utils.MIME_TYPE_ZIP);
					String zipFileName = null;
					if (Utils.isItMailArchiveZip(destination_type)) {
						zipFileName = site_id + "_mailarchive.zip";
						log.info("*** This is Mail Archive Zip Download ***");
					} else if (Utils.isItMailArchiveMbox(destination_type)) {
						zipFileName = site_id + "_mailarchivembox.zip";
						log.info("*** This is Mail Archive (Mbox) Zip Download ***");
					}
					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					ZipOutputStream out = new ZipOutputStream(response.getOutputStream());
					String compressionLevel = env.getProperty(Utils.ENV_ZIP_COMPRESSSION_LEVEL);
					if ((compressionLevel == null) || (compressionLevel.isEmpty())) {
						// set compression level to high by default
						out.setLevel(9);
						log.error("The property \"zip.compression.level\" is not set");
					} else {
						out.setLevel(Integer.parseInt(compressionLevel.trim()));
					}

					log.info("Starting mail archive download for site " + site_id);
					downloadStatus = getMailArchiveZipContent(env, site_id, downloadStatus,
							sessionId, httpContext, out, request,migrationId);

					out.flush();
					out.close();
					log.info("Finished mail archive download for site " + site_id);


				} catch (RestClientException e) {
					String errorMessage = Utils.STATUS_FAILURE + " Cannot find site by siteId: " + site_id
							+ " " + e.getMessage();
					Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
					.type(MediaType.TEXT_PLAIN).build();
					log.error(errorMessage);
					downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, errorMessage);

				} catch (Exception e) {
					String errorMessage = Utils.STATUS_FAILURE + " Migration status for " + site_id + " "
							+ e.getClass().getName();
					Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).type(MediaType.TEXT_PLAIN)
					.build();
					log.error("downloadMailArchiveZipFile ", e);
					downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, errorMessage);
				}
			} else {
				String userError = "Cannot become user " + userId;
				log.error(userError);
				downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, userError);
			}
			// update the status and end_time of migration record
			setMigrationEndTimeAndStatus(migrationId, repository, downloadStatus);

			return;
		}

	private JSONObject errHandlingInDownloadMailArchiveZipFile(String site_id, JSONObject downloadStatus, String errorMessage) {
		downloadStatus.put(Utils.MIGRATION_STATUS, Utils.REPORT_STATUS_ERROR);
		JSONObject count = (JSONObject)downloadStatus.get(Utils.REPORT_ATTR_COUNTS);
		int errorCount = (Integer)count.get(Utils.REPORT_ATTR_COUNTS_ERRORS);
		count.put(Utils.REPORT_ATTR_COUNTS_ERRORS, errorCount+1);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS,errHandlingForZiparchive(site_id,errorMessage));
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,count);
		return downloadStatus;
	}

	private JSONObject migrationStatusObject(String destination_type) {
		JSONObject downloadStatus = new JSONObject();
		downloadStatus.put(Utils.REPORT_ATTR_TYPE,destination_type);
		downloadStatus.put(Utils.MIGRATION_STATUS, "");
		JSONObject counts = new JSONObject();
		counts.put(Utils.STATUS_SUCCESSES,0);
		counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS,0);
		counts.put(Utils.REPORT_STATUS_PARTIAL,0);
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,counts);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS,new JSONArray());
		return downloadStatus;
	}

	/**
		 * get MailArchive message content into ZipOutputStream
		 * @param env
		 * @param site_id
		 * @param downloadStatus
		 * @param sessionId
		 * @param httpContext
		 * @param out
		 * @return
		 * @throws IOException
		 */
		private JSONObject getMailArchiveZipContent(Environment env, String site_id,
				JSONObject downloadStatus, String sessionId,
				HttpContext httpContext, ZipOutputStream out, HttpServletRequest request, String migrationId)  {


			// get all mail channels inside the site
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+ "direct/mailarchive/siteChannels/" + site_id + ".json?_sessionId="
					+ sessionId;
			JSONObject channelsJSON = null;
			channelsJSON = new JSONObject(restTemplate.getForObject(requestUrl,
					String.class));

			if (!channelsJSON.has(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION))
			{
				return downloadStatus;
			}

			JSONArray channels = channelsJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);

			boolean folderForChannels = false;
			if (channels.length() > 1)
			{
				// if the site have more than one MailArchive channel
				// create zip folder for each channel
				folderForChannels = true;
			}
			for (int iChannel = 0; iChannel < channels.length(); iChannel++) {
				JSONObject channel = channels.getJSONObject(iChannel);
				String channelId = channel.getString("data");
				channelId = channelId.substring(("/mailarchive/channel/" + site_id + "/").length());
				String channelName = channel.getString("displayTitle");

				if (folderForChannels)
				{
					ZipEntry folderEntry = new ZipEntry(channelName + "/");
					try {
						out.putNextEntry(folderEntry);
					} catch (IOException e) {
						String ioError = "downloadMailArchiveZipFile: problem adding zip folder for MailArchive channel "
								+ channelName + " " + e;
						log.error(ioError);
					}
				}

				// get all email messages in the channel
				requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
						+ "direct/mailarchive/channelMessages/" + site_id + "/" + channelId + ".json?_sessionId="
						+ sessionId;
				JSONObject messagesJSON = new JSONObject(restTemplate.getForObject(requestUrl,
						String.class));
				JSONArray messages = messagesJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);
				Map<String, String[]> parameterMap = request.getParameterMap();
				String destination_type = parameterMap.get("destination_type")[0];

				if (Utils.isItMailArchiveZip(destination_type)) {
					JSONArray mailZipMessagesStatus = new JSONArray();
					for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
						JSONObject singleMailZipMsgStatus = new JSONObject();
						JSONObject message = messages.getJSONObject(iMessage);

						// create file for each message
						String messageFolderName = getMailArchiveMessageFolderName(message, channelName, folderForChannels);

						singleMailZipMsgStatus.put(Utils.REPORT_ATTR_ITEM_ID, messageFolderName);

						// 1. write the message file
						singleMailZipMsgStatus = handleMailArchiveMessage(out, message, messageFolderName,
								singleMailZipMsgStatus);

						// 2. get attachments, if any
						singleMailZipMsgStatus = handleMailArchiveMessageAttachments(
								sessionId, httpContext, out, message, messageFolderName, singleMailZipMsgStatus);

						mailZipMessagesStatus.put(singleMailZipMsgStatus);
					}
					downloadStatus.put(Utils.REPORT_ATTR_ITEMS, mailZipMessagesStatus);

					downloadStatus = finalReportObjBuilderForMailZipMigration(downloadStatus, mailZipMessagesStatus);

				} else if (Utils.isItMailArchiveMbox(destination_type)) {
					String messageFolderName = getMailArchiveMboxMessageFolderName(site_id, channelName, folderForChannels);
					StringBuilder msgBundle = new StringBuilder();
					// this array hold the overall successes,errors for all the messages in a site.
					JSONArray mboxMessagesStatus = new JSONArray();
					for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
						JSONObject message = messages.getJSONObject(iMessage);
						String date= getProperty(message,Utils.JSON_ATTR_MAIL_DATE);
						String subject= getProperty(message,Utils.JSON_ATTR_MAIL_SUBJECT);
						String messageId=date+" "+subject;
						String emailMessage = message.toString();
						AttachmentHandler attachmentHandler = new AttachmentHandler(request);
						attachmentHandler.setEnv(env);
						EmailFormatter emailFormatter = null;
						try {
							 emailFormatter = new EmailFormatter(emailMessage, attachmentHandler);
						}catch (IOException e){
							String msg = "Mbox zip file could not be downloaded due to bad json response";
							mboxMessagesStatus.put(errHandlingForZiparchive(messageId,msg));
							log.error(msg +"for message: "+messageId+ " "+ e.getMessage());
							continue;
						}
						MailResultPair mboxFormatTextPlusStatus = emailFormatter.mboxFormat();
						String mboxMessage = mboxFormatTextPlusStatus.getMessage();
						JSONObject singleMboxMsgStatus = mboxFormatTextPlusStatus.getReport().getJsonReportObject();
						if(mboxMessage!=null) {
							msgBundle.append(mboxMessage);
							msgBundle.append("\r\n");
							mboxMessagesStatus.put(singleMboxMsgStatus);
						}else {
							mboxMessagesStatus.put(singleMboxMsgStatus);
							log.error(String.format("Mbox Formatting for message with Id (%s) not successful with migrationid " +
									"(%s) for site (%s)",messageId,migrationId,site_id));
						}
					}
					mboxMessagesStatus = handleMailArchiveMboxMessage(out, msgBundle.toString(), messageFolderName,
								mboxMessagesStatus);

					downloadStatus= finalReportObjBuilderForMailZipMigration(downloadStatus, mboxMessagesStatus);

				}
			}
			return downloadStatus;
		}

	private JSONObject finalReportObjBuilderForMailZipMigration(JSONObject downloadStatus, JSONArray mboxMessagesStatus) {
		JSONArray errAndPartialSuccessList = new JSONArray();
		int successes,errors,partial;
		successes = errors=partial=0;
		for (int i = 0; i < mboxMessagesStatus.length(); i++) {
			JSONObject perMsg = mboxMessagesStatus.getJSONObject(i);
			String msgStatus = (String) perMsg.get(Utils.REPORT_ATTR_ITEM_STATUS);
			if (msgStatus.equals(Utils.REPORT_STATUS_OK)) {
				successes = successes + 1;
			} else if (msgStatus.equals(Utils.REPORT_STATUS_PARTIAL)) {
				partial = partial + 1;
				errAndPartialSuccessList.put(mboxMessagesStatus.get(i));
			} else if (msgStatus.equals(Utils.REPORT_STATUS_ERROR)) {
				errors = errors + 1;
				errAndPartialSuccessList.put(mboxMessagesStatus.get(i));
			}
		}
		JSONObject counts = new JSONObject();
		counts.put(Utils.STATUS_SUCCESSES,successes);
		counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS,errors);
		counts.put(Utils.REPORT_STATUS_PARTIAL,partial);
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,counts);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS,errAndPartialSuccessList);
		if (errors > 0) {
			downloadStatus.put(Utils.MIGRATION_STATUS, Utils.REPORT_STATUS_ERROR);
		} else if (partial > 0 & successes > 0) {
			downloadStatus.put(Utils.MIGRATION_STATUS, Utils.REPORT_STATUS_PARTIAL);
		} else
			downloadStatus.put(Utils.MIGRATION_STATUS, Utils.REPORT_STATUS_OK);
		return downloadStatus;
	}

	private String getProperty(JSONObject message, String jsonAttribute) {
		JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
		String date = getHeaderAttribute(headers, jsonAttribute);;
		return date;
	}


	/**
		 * output MailArchive message content
		 * @param out
		 * @param message
		 * @param messageFolderName
		 * @param messageStatus
		 * @return
		 * @throws IOException
		 */
		private JSONObject handleMailArchiveMessage(ZipOutputStream out,
				JSONObject message, String messageFolderName,
				JSONObject messageStatus)  {
			try {
				// get the html file content first
				String messageContent = message.has(Utils.JSON_ATTR_MAIL_BODY)?message.getString(Utils.JSON_ATTR_MAIL_BODY):"";

				ZipEntry fileEntry = new ZipEntry(messageFolderName + Utils.MAIL_MESSAGE_FILE_NAME);
				out.putNextEntry(fileEntry);

				// output message header info
				JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
				for (int iHeader = 0; iHeader<headers.length(); iHeader++)
				{
					String header = headers.getString(iHeader) + "\r\n";
					out.write(header.getBytes());
				}
				out.write("\r\n".getBytes());
				// output message body
				out.write(messageContent.getBytes());


				messageStatus.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_OK);
			} catch (java.net.MalformedURLException e) {
				// return status with error message
				messageStatus.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
				messageStatus.put(Utils.JSON_ATTR_MESSAGE,"problem getting message content");
			} catch (IOException e) {
				// return status with error message
				messageStatus.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
				messageStatus.put(Utils.JSON_ATTR_MESSAGE,"problem getting message content");
			}
			return messageStatus;
		}

		private JSONArray handleMailArchiveMboxMessage(ZipOutputStream out,
				String message, String messageFolderName,
				JSONArray messagesStatus) {

			try {
				// get the html file content first

				ZipEntry fileEntry = new ZipEntry(messageFolderName + Utils.MAIL_MBOX_MESSAGE_FILE_NAME);
				out.putNextEntry(fileEntry);

				// output message body
				out.write(message.getBytes());


			} catch (java.net.MalformedURLException e) {
				String msg = "Mbox zip file could not be downloaded due to MalformedURLException ";
				messagesStatus.put(errHandlingForZiparchive(messageFolderName,  msg));
				log.error(msg+e.getMessage());
			} catch (IOException e) {
				String msg = "Mbox zip file could not be downloaded due to IOException ";
				messagesStatus.put(errHandlingForZiparchive(messageFolderName,msg));
				log.error(msg+e.getMessage());
			}
			return messagesStatus;
		}

	private JSONObject errHandlingForZiparchive(String msgIdentifier, String errMsg) {
		JSONObject errRes=new JSONObject();
		errRes.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		errRes.put(Utils.JSON_ATTR_MESSAGE, errMsg);
		errRes.put(Utils.REPORT_ATTR_ITEM_ID,msgIdentifier);
		return errRes;
	}

	/**
		 * put mail message attachments into zip
		 * @param sessionId
		 * @param httpContext
		 * @param out
		 * @param message
		 * @param messageFolderName
		 * @return
		 */
		private JSONObject handleMailArchiveMessageAttachments(String sessionId,
				HttpContext httpContext, ZipOutputStream out, JSONObject message,
				String messageFolderName, JSONObject messageStatus) {
			JSONArray attachmentsStatus = new JSONArray();
			JSONArray attachments = message.getJSONArray(Utils.JSON_ATTR_MAIL_ATTACHMENTS);
			for (int iAttachment = 0; iAttachment < attachments.length(); iAttachment++) {
				// get each attachment
				JSONObject attachment = attachments.getJSONObject(iAttachment);
				String type = attachment.has(Utils.JSON_ATTR_MAIL_TYPE)?attachment.getString(Utils.JSON_ATTR_MAIL_TYPE):"";
				String name = attachment.has(Utils.JSON_ATTR_MAIL_NAME)?attachment.getString(Utils.JSON_ATTR_MAIL_NAME):"";
				String url = attachment.has(Utils.JSON_ATTR_MAIL_URL)?attachment.getString(Utils.JSON_ATTR_MAIL_URL):"";
				// Call the zipFiles method for creating a zip stream.
				String fileStatus = zipFiles(type, httpContext,
						messageFolderName + name, name, "", url,
						sessionId, out, new HashMap<String, String>());
				JSONObject attachmentStatus = new JSONObject();
				attachmentStatus.put(Utils.JSON_ATTR_MAIL_NAME, name);
				attachmentStatus.put(Utils.MIGRATION_STATUS, fileStatus);

				// add the attachment status to the list
				attachmentsStatus.put(attachmentStatus);
			}

			// update message status
			messageStatus.put(Utils.JSON_ATTR_MAIL_ATTACHMENTS, attachmentsStatus);

			return messageStatus;
		}

		/**
		 * construct the zip folder name for a MailArchive message
		 * @param message
		 * @param channelName
		 * @param folderForChannels
		 * @return
		 */
		private String getMailArchiveMessageFolderName(JSONObject message, String channelName, boolean folderForChannels)
		{
			// get message information from header
			JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
			String subject = getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_SUBJECT);
			String sender = getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_FROM);
            		if (sender.indexOf('<') != -1) {
                		sender = sender.substring(sender.indexOf('<') + 1, sender.indexOf('>'));
            		}
            		String date = getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_DATE);

			// create file for each message
			String messageFolderName = "";
			if (folderForChannels)
			{
				messageFolderName = channelName + Utils.PATH_SEPARATOR;
			}
			messageFolderName = Utils.sanitizeName(Utils.COLLECTION_TYPE, messageFolderName + " " + sender+ " "+date+ " "+subject) + "/";

			return messageFolderName;
		}

		private String getMailArchiveMboxMessageFolderName(String site_id, String channelName, boolean folderForChannels) {
			// get message information from header
			String messageFolderName = "";
			if (folderForChannels) {
				messageFolderName = channelName + Utils.PATH_SEPARATOR;
			}
			messageFolderName = Utils.sanitizeName(Utils.COLLECTION_TYPE, messageFolderName + site_id) + "/";

			return messageFolderName;
		}

		/**
		 * get mail message info from header
		 * @param headers
		 * @param attribute
		 * @return
		 */
		private String getHeaderAttribute(JSONArray headers, String attribute)
		{
			String rv = "";
			for (int iHeader = 0; iHeader < headers.length(); iHeader++) {
				String header = headers.getString(iHeader);
				if (header.startsWith(attribute))
				{
					rv = header.substring(attribute.length());
					break;
				}
			}

			return rv;
		}

		public HashMap<String, String> processAddEmailMessages(
				HttpServletRequest request, HttpServletResponse response,
				String target, String remoteUser, HashMap<String, String> rv,
				String googleGroupId, String siteId, String toolId,
				HashMap<String, Object> saveMigration) {
			if (!saveMigration.containsKey("migration")) {
				// no new Migration record created
				rv.put("errorMessage", "Cannot create migration records for user "
						+ remoteUser + " and site=" + siteId);
				return rv;
			}

			Migration migration = (Migration) saveMigration.get("migration");
			String migrationId = migration.getMigration_id();

			// get session
			HashMap<String, Object> sessionAttributes = Utils
					.login_becomeuser(env, request, remoteUser);
			if (sessionAttributes == null || !sessionAttributes.containsKey(Utils.SESSION_ID)) {
				rv.put("errorMessage", "Cannot create become user "
						+ remoteUser + " and site=" + siteId);
				return rv;
			}

			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			// get all mail channels inside the site
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+ "direct/mailarchive/siteChannels/" + siteId + ".json?_sessionId="
					+ sessionId;
			JSONObject channelsJSON = null;
			channelsJSON = new JSONObject(restTemplate.getForObject(requestUrl,
					String.class));

			if (!channelsJSON.has(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION))
			{
				rv.put("errorMessage", "Cannot get mail archive information for site=" + siteId);
				return rv;
			}

			JSONArray channels = channelsJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);
			for (int iChannel = 0; iChannel < channels.length(); iChannel++) {
				JSONObject channel = channels.getJSONObject(iChannel);
				String channelId = channel.getString("data");
				channelId = channelId.substring(("/mailarchive/channel/" + siteId + "/").length());
				String channelName = channel.getString("displayTitle");

				// get all email messages in the channel
				requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
						+ "direct/mailarchive/channelMessages/" + siteId + "/" + channelId + ".json?_sessionId="
						+ sessionId;
				JSONObject messagesJSON = new JSONObject(restTemplate.getForObject(requestUrl,
						String.class));
				JSONArray messages = messagesJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);
				for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
					JSONObject message = messages.getJSONObject(iMessage);
					String messageId = message.getString("id");
					// construct the MigrationEmailMessage object
					MigrationEmailMessage mMessage = new MigrationEmailMessage(
							messageId, migrationId,
							remoteUser, googleGroupId,
							message.toString(), null,
							null, null);
					try
					{
						mRepository.save(mMessage);
					}
					catch (Exception e)
					{
						log.error("Problem saving the MigrationEmailMessage " + messageId + " with GoogleGroupId " + googleGroupId + " into database ");
					}


				}
			}
			return rv;
		}

		/**
		 * TODO
		 * call GG microservice to upload message content
		 * @param googleGroup
		 * @param rcf822Email
		 * @return
		 */

		ApiResultWrapper addEmailToGoogleGroup(String googleGroup, String rcf822Email) {
			log.info("addEmailToGoogleGroup: group: {}",googleGroup);
			log.debug("addEmailToGoogleGroup: email: {}",rcf822Email);

			GGBApiWrapper ggb = establishGGBConnection();

			String archive_url = "/groups/"+googleGroup+"/messages";
			ApiResultWrapper arw= ggb.post_request(archive_url,rcf822Email);
			return arw;

		}

		// Get the json version of the site info.
		protected JSONObject getSiteInfoJson(String sessionId, String siteId) {

			// get the site info from ctools
			JSONObject siteJSONObject = null;

			// get site info for site as json.
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
			String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+ "direct/site/" + siteId + ".json?_sessionId=" + sessionId;
			log.debug("siteInfo url: " + requestUrl);
			try {
				String siteJson = restTemplate.getForObject(requestUrl,
						String.class);
				siteJSONObject = new JSONObject(siteJson);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
				throw e;
			}
			return siteJSONObject;
		}

		/////////////////
		// Get the information required to setup the Google Group from CTools.

		// The group email address might come from various sources.
		public JSONObject getCToolsGroupInfoJson(String sessionId,
				String siteId, String group_email) {
			JSONObject siteJSONObject = getSiteInfoJson(sessionId,siteId);
			return create_group_info_object(siteId, group_email, siteJSONObject);
		}

		public static JSONObject create_group_info_object(String siteId, String group_email, JSONObject siteJSONObject) {
			JSONObject group_info = new JSONObject();

			// include site id for tracking purposes.
			group_info.put("site_id",siteId);
			group_info.put("title", siteJSONObject.optString("title",""));
			group_info.put("group_email",group_email);
			group_info.put("description", siteJSONObject.optString("description",""));

			log.debug(String.format("group_info: [%s]",group_info.toString()));
			return group_info;
		}

		// Get the new email prefix from the old one used in the archive.
		public JSONObject getCToolsGroupInfoJson(String sessionId,String siteId) {
			return getCToolsGroupInfoJson(sessionId,siteId,getArchiveEmail(sessionId,siteId));
		}

		//https://ctdevsearch.dsc.umich.edu/direct/mailarchive/siteMessages/22b5d237-0a22-4995-a4b1-d5022dd90a86.json
		// Get the new Google email address based on the email name available in the archive.
		protected String getArchiveEmail(String sessionId, String siteId) {
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+"/direct/mailarchive/siteMessages/"+siteId+".json?_sessionId=" + sessionId;
			String archiveJson = null;
			try {
				archiveJson = restTemplate.getForObject(requestUrl,String.class);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
				// Don't hide the error.
				throw e;
			}

			String archiveEmail = extractArchiveEmailName(archiveJson);
			String suffix = env.getProperty(Utils.GGB_GOOGLE_GROUP_DOMAIN);
			if (suffix == null) {
				log.warn("no google suffix provided. Property name is: {}",Utils.GGB_GOOGLE_GROUP_DOMAIN);
				suffix = "";
			}

			String group_name = archiveEmail+"@"+suffix;
			log.info("from extractArchiveEmailName: {} ",archiveEmail);

			return group_name;
		}

		// Breakout the email name used in CTools for this archive.
		protected String extractArchiveEmailName(String emailArchive) {
			JSONObject jo = new JSONObject(emailArchive);
			JSONArray ja = jo.getJSONArray("mailarchive_collection");
			JSONArray firstHeaders = (JSONArray) ((JSONObject)ja.get(0)).get("headers");

			int myJsonArraySize = firstHeaders.length();
			String archive_email_name = null;

			for (int i = 0; i < myJsonArraySize && archive_email_name == null; i++) {
				String header  = (String) firstHeaders.get(i);
				// look for the To: header
				if (! header.startsWith("To: ")) {
					continue;
				}
				// Pull out the email name used by the old archive.
				log.debug("aAEN: To header: {}",header);
				archive_email_name = extractEmailFromToHeader(header);
				break;
			}
			log.debug("eAEN: returning: {}",archive_email_name);
			return archive_email_name;
		}

		static public String extractEmailFromToHeader(String header) {
			String archive_email_name;
			// Need to remove this if present.
			header = header.replace("<","");
			int at_index = header.indexOf("@");
			int to_index = header.indexOf("To: ");
			archive_email_name = header.substring(to_index+4,at_index);
			return archive_email_name;
		}

		/**
		 * TODO
		 * call GG microservice to get Group Groups settings for given site id
		 * @param siteId
		 * @param sessionId
		 * @return
		 */

		public ApiResultWrapper createGoogleGroupForSite(JSONObject googleGroupSettings) {

			GGBApiWrapper ggb = establishGGBConnection();

			String new_group_url = String.format("/groups/%s",googleGroupSettings.getString("email"));
			ApiResultWrapper arw = ggb.put_request(new_group_url,googleGroupSettings.toString());

			log.warn("check for errors");

			return  arw;

		}

		public GGBApiWrapper establishGGBConnection() {
			
			String server = env.getProperty(Utils.GGB_SERVER_NAME);
			String ggb_authinfo_username = env.getProperty(Utils.GGB_AUTHINFO_BASICAUTH_USERNAME);
			String ggb_authinfo_password = env.getProperty(Utils.GGB_AUTHINFO_BASICAUTH_PASSWORD);
			HashMap<String,String> basicAuthInfo = createBasicAuthInfo(ggb_authinfo_username,ggb_authinfo_password);			

			GGBApiWrapper ggb = new GGBApiWrapper(server,basicAuthInfo);
			return ggb;
		}
		
		HashMap<String,String> createBasicAuthInfo(String username,String password) {

			HashMap<String,String >authInfo = new HashMap<String,String>();
			authInfo.put("userName",username);
			authInfo.put("password",password);
			return authInfo;
		}

		// Change the ctools site information into Google group information.
		protected JSONObject getGoogleGroupSettings(String sessionId, String siteId) {
			// get group information for this site and update Google
			JSONObject group_info = getCToolsGroupInfoJson(sessionId,siteId);

			log.debug(String.format("migration: group info: [%s]",group_info.toString()));

			JSONObject googleGroupSettings = createGoogleGroupJson(group_info);
			return googleGroupSettings;
		}

		// create the json Google expects to create a group
		protected JSONObject createGoogleGroupJson(JSONObject group_info) {
			log.warn("createGoogleGroupJson: HACK, GROUP INFO IS NOT YET CORRECT. E.G. DESCRIPTION IS WRONG.");

			log.debug("createGoogleGroupJson: {}",group_info.toString());

			JSONObject googleGroupJson = new JSONObject();
			googleGroupJson.put("email", group_info.get("group_email"));
			googleGroupJson.put("name", group_info.get("title"));
			googleGroupJson.put("description", formatDescription((String)group_info.get("description")));
			log.debug("ggbjson: "+googleGroupJson.toString());

			return googleGroupJson;
		}

		// Make description fit Google standards.
		protected String formatDescription(String description) {
			final int MAX_LENGTH=300;
			String formatted_string = null;
			formatted_string = description.substring(0, Math.min(description.length(), MAX_LENGTH));
			// override description until we know correct format.
			formatted_string = "DUMMY DESCRIPTION FROM formatDescription";
			return formatted_string;
		}


		// map the CTools role to the Google role
		public String findGoogleRole(String role) {
			String goole_role = MemberRoleMap.get(role);
			return (goole_role != null) ? goole_role : MemberRoleMap.get("DEFAULT");
		}

		// change members list to one suitable for inserting into google.
		public List<List<String>> memberPropertiesList(HashMap<String,String> members,String defaultEmailSuffix) {
			log.debug("members: {}",members);

			List<List<String>> edited_members = new ArrayList<List<String>>();
			for (Map.Entry<String, String> entry : members.entrySet()) {
				String user = entry.getKey();
				String role = entry.getValue();
				log.debug("members: member: {} role: {}",user,role);
				List<String> pair = new ArrayList<String>();
				if (user.indexOf("@") == -1) {
					user = user + "@"+defaultEmailSuffix;
				}
				pair.add(user);
				pair.add(findGoogleRole(role));
				edited_members.add(pair);
			}
			log.debug("edited_members: {}",edited_members);
			return edited_members;
		}


		public String updateGoogleGroupMembershipFromSite(String sessionId,String siteId,HashMap<String, String> members) {

			log.debug("process members for site: "+siteId);
			List<List<String>> membersProperties = memberPropertiesList(members,Utils.DEFAULT_EMAIL_MEMBER_SUFFIX);
			log.debug("found members for site: "+siteId+" "+membersProperties);

			JSONObject ggs = getGoogleGroupSettings(sessionId,  siteId);

			addMembersToGroup(ggs.getString("email"),membersProperties);
			//TODO: error handling
			return null;
		}

		// TODO: proper return value. and status handling.
		// What is the result of the
		String addMembersToGroup(String group_id,List<List<String>> membersProperties) {

			for (List<String> user : membersProperties) {
				log.debug("group: {} user: {} role: {}",group_id,user.get(0),user.get(1));
				addMemberToGroup(group_id,user.get(0),user.get(1));
			}

			return "MAYBE";
		}


		ApiResultWrapper addMemberToGroup(String group_id, String member_email, String member_role) {

			GGBApiWrapper ggb = establishGGBConnection();

			String new_member_url = String.format("/groups/%s/members/%s",group_id,member_email);

			JSONObject jo = new JSONObject();
			jo.put("email",member_email);
			jo.put("role",member_role);
			ApiResultWrapper result = ggb.put_request(new_member_url,jo.toString());
			return result;
		}

		/**
		 * migrate email content to Group Group using microservice
		 * @param message
		 * @return
		 */
		@Async
		protected Future<String> uploadMessageToGoogleGroup(MigrationEmailMessage message) {

			String googleGroupId = message.getGoogle_group_id();

			String messageId = message.getMessage_id();
			log.info("begin to upload message " + messageId  + " to Google Group id = " + googleGroupId);

			// use EmailFormatter to get RFC822 complaint email content
			String emailContent = message.getJson();
			HttpServletRequest request = null;
			AttachmentHandler attachmentHandler = new AttachmentHandler(request);
			attachmentHandler.setEnv(env);

			String emailText;
			JSONObject statusObj=new JSONObject();
			statusObj.put(Utils.REPORT_ATTR_ITEM_ID, messageId);

			// Try only once and record the resulting status in the database.
			try
			{
				EmailFormatter formatter = new EmailFormatter(emailContent, attachmentHandler);
				formatter.setEnv(env);
				MailResultPair emailTextPlusStatus = formatter.rfc822Format();
				emailText = emailTextPlusStatus.getMessage();
				statusObj = emailTextPlusStatus.getReport().getJsonReportObject();

				if (emailText != null) {
					// mark the file as being processed
					mRepository.setMigrationMessageStartTime(message.getMessage_id(), new Timestamp(System.currentTimeMillis()));

					// process the message
					ApiResultWrapper arw = addEmailToGoogleGroup(googleGroupId, emailText);
					int statusCode = arw.getStatus();
					// taking the success msg
					String ggbResult = arw.getResult();
					// Taking the error message
					String ggbMsg = arw.getMessage();
					log.debug("uploadMessageToGoogleGroup: status: googleGroupId: {}", statusCode, googleGroupId);

					if (statusCode / 100 != 2 && statusCode != 409)  {
						statusObj=errorHandlingWhenNot200(statusObj, statusCode, ggbMsg);
						log.error(String.format("Failure in migrating message with MessageId: \"%1$s\" to google groups" +
								", status code %2$d and error message %3$s", messageId, statusCode, ggbMsg));
						return new AsyncResult<String>(statusObj.toString());
					}

					// Upload to Google groups went fine
					String messageStatus = (String) statusObj.get(Utils.REPORT_ATTR_ITEM_STATUS);
					String messageStr = (String) statusObj.get(Utils.JSON_ATTR_MESSAGE);
					//This is the case when in the EmailFormatter attachment might have dropped due to some error or size limit
					if (messageStatus == Utils.REPORT_STATUS_PARTIAL) {
						statusObj.put(Utils.REPORT_ATTR_ITEM_STATUS, "Google Groups upload Went fine but " + messageStr);
					}
					log.info("The response from google groups when 200: "+ggbResult+" for MessageId: "+messageId);
				}

			}catch (IOException exception) {
				String errorString = "IOException from EmailFormatter for message id " + messageId + " " + exception.getMessage();
				log.error(errorString);
				statusObj=errHandlingWhenExceptions(statusObj);
			}catch (ParseException e){
				String errorString = "ParseException while extracting response from GGB with message id " + messageId + " " + e.getMessage();
				log.error(errorString);
				statusObj=errHandlingWhenExceptions(statusObj);
			}catch (Exception e) {
				String msg = String.format("unexpected exception in uploadMessageToGoogleGroup: %s for messageId: %s",
						e.getMessage(),messageId);
				log.error(msg);
				statusObj=errHandlingWhenExceptions(statusObj);
			}

			finally {
				// update the status and end time for file item
				mRepository.setMigrationMessageEndTime(messageId, new java.sql.Timestamp(System.currentTimeMillis()));
				mRepository.setMigrationMessageStatus(messageId, statusObj.toString());
			}
			log.debug("uploadMessageToGoogleGroup: return");
			return new AsyncResult<String>(statusObj.toString());
		}

	private JSONObject errHandlingWhenExceptions(JSONObject statusObj) {
		statusObj.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		statusObj.put(Utils.JSON_ATTR_MESSAGE, "Failure in upload to Google Groups");
		return statusObj;
	}

	private JSONObject errorHandlingWhenNot200(JSONObject statusObj, int statusCode, String errMsg) {
		statusObj.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		statusObj.put(Utils.JSON_ATTR_MESSAGE, errMsg + " " + statusCode);
		return statusObj;

	}

}
