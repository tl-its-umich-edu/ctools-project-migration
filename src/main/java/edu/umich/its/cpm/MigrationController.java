package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.inject.Inject;

import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.context.annotation.PropertySource;

import org.json.JSONObject;
import org.json.JSONArray;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;

import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.axis.encoding.XMLType;
import javax.xml.rpc.ParameterMode;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

@PropertySource("file:${catalina.home:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@RestController
public class MigrationController {

	private static final String BOX_CLIENT_ID = "box_client_id";
	private static final String BOX_CLIENT_SECRET = "box_client_secret";
	private static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";

	private static final String CTOOLS_ACCESS_STRING = "/access/content";

	private static final String COLLECTION_TYPE = "collection";

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	@Autowired
	MigrationRepository repository;

	@Autowired
	private Environment env;

	@Context
	// injected response proxy supporting multiple threads
	private HttpServletResponse response;

	@Context
	// injected request proxy supporting multiple threads
	private HttpServletRequest request;

	// WSDL operation
	private static final String OPERATION_GET_CONTENT_DATA = "getContentData";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID = "sessionid";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID = "resourceId";

	// String values used in content json feed
	private static final String CONTENT_JSON_ATTR_CONTENT_COLLECTION = "content_collection";
	private static final String CONTENT_JSON_ATTR_CONTAINER = "container";
	private static final String CONTENT_JSON_ATTR_TITLE = "title";
	private static final String CONTENT_JSON_ATTR_TYPE = "type";
	private static final String CONTENT_JSON_ATTR_URL = "url";

	/**
	 * get all CTools sites where user have site.upd permission
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/projects")
	public void getProjectSites(HttpServletRequest request,
			HttpServletResponse response) {
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/withPerm/.json?permission=site.upd"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/site/withPerm/.json?permission=site.upd&_sessionId="
					+ sessionId;
			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				errorMessage = requestUrl + e.getMessage();
				log.error(errorMessage);
			}

			// JSON response
			JSON_response(response, rv, errorMessage, requestUrl);
		}
	}

	/**
	 * get page information
	 * 
	 * @param site_id
	 * @return
	 */
	@GET
	@RequestMapping("/projects/{site_id}")
	public void getProjectSitePages(@PathVariable String site_id,
			HttpServletRequest request, HttpServletResponse response) {
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/site/" + site_id + "/pages.json?_sessionId="
					+ sessionId;

			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				errorMessage = "Cannot find site by siteId: " + site_id + " "
						+ e.getMessage();
				log.error(errorMessage);
			}

			// JSON response
			JSON_response(response, rv, errorMessage, requestUrl);
		}
	}

	/**
	 * login into CTools and become user with sessionId
	 */
	private String login_becomeuser(HttpServletRequest request) {
		// return the session id after login
		String sessionId = "";

		String remoteUser = request.getRemoteUser();
		log.info("remote user is " + remoteUser);
		// here is the CTools integration prior to CoSign integration ( read
		// session user information from configuration file)
		// 1. create a session based on user id and password
		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
		String requestUrl = env.getProperty("ctools.server.url")
				+ "direct/session?_username=" + env.getProperty("username")
				+ "&_password=" + env.getProperty("password");
		log.info(requestUrl);
		ResponseEntity<String> response = restTemplate.postForEntity(
				requestUrl, null, String.class);
		HttpStatus status = response.getStatusCode();
		if (!status.equals(HttpStatus.CREATED)) {
			// return error if a new CTools session could not be created using
			// username and password provided
			log.info("Wrong user id or password. Cannot login to CTools "
					+ env.getProperty("ctools.server.url"));
		} else {
			// get the session id
			sessionId = response.getBody();
			log.info("successfully logged in as user "
					+ env.getProperty("username") + " with sessionId = "
					+ sessionId);

			// 2. become the user based on REMOTE_USER setting after CoSign
			// integration
			restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/session/SESSION_ID.json"
			requestUrl = env.getProperty("ctools.server.url")
					+ "direct/session/becomeuser/" + remoteUser
					+ ".json?_sessionId=" + sessionId;
			log.info(requestUrl);

			try {
				String resultString = restTemplate.getForObject(requestUrl,
						String.class, sessionId);
				log.info(resultString);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());

				// nullify sessionId if become user call is not successful
				sessionId = null;
			}
		}
		return sessionId;
	}

	/**
	 * get all migration records
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrations")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrations() {
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findAll()).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migration records" + e.getMessage())
					.build();
		}
	}

	/**
	 * get a specific migration record
	 * 
	 * @param migration_id
	 * @return
	 */
	@GET
	@RequestMapping("/migrations/{migration_id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrations(@PathVariable("migration_id") String migration_id) {
		Object o = repository.findOne(migration_id);
		String rv = "";
		if (o == null) {
			// no such migration record
			throw new MigrationNotFoundException(
					"no matching record for /migrations/" + migration_id);
		}
		// find migration record with id
		return Response.status(Response.Status.OK).entity((Migration) o)
				.build();
	}

	/**
	 * found all migrated records (where the migration record have "end" field
	 * value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrated")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Migration> migrated() {
		return repository.findMigrated();
	}

	/**
	 * insert a new record of Migration
	 * 
	 * @param request
	 */
	@POST
	@RequestMapping(value = "/migration")
	@Produces(MediaType.APPLICATION_JSON)
	public void migration(HttpServletRequest request,
			HttpServletResponse response) {
		Map<String, String[]> parameterMap = request.getParameterMap();
		Migration m = new Migration(parameterMap.get("site_id")[0],
				parameterMap.get("site_name")[0],
				parameterMap.get("tool_id")[0],
				parameterMap.get("tool_name")[0], request.getRemoteUser(),
				new java.sql.Timestamp(System.currentTimeMillis()), // start
																	// time is
																	// now
				null, // no end time
				parameterMap.get("destination_type")[0], null);

		Migration newMigration = null;

		StringBuffer insertMigrationDetails = new StringBuffer();
		insertMigrationDetails.append("Save migration record site_id=")
				.append(parameterMap.get("site_id")[0]).append(" site_name=")
				.append(parameterMap.get("site_name")[0]).append(" tool_id=")
				.append(parameterMap.get("tool_id")[0]).append(" tool_name=")
				.append(parameterMap.get("tool_name")[0])
				.append(" migrated_by=").append(request.getRemoteUser())
				.append(" destination_type=")
				.append(parameterMap.get("destination_type")[0]).append(" \n ");
		try {
			newMigration = repository.save(m);
		} catch (Exception e) {
			log.error("Exception " + insertMigrationDetails.toString()
					+ e.getMessage());
		}

		try {
			if (newMigration != null) {
				log.info("migration", newMigration);
				// new Migration record created
				// set HTTP code to "201 Created"
				response.setStatus(HttpServletResponse.SC_CREATED);
				response.getWriter().write(
						(new JSONObject(newMigration)).toString());
			} else {
				// no new Migration record created
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().println(
						new JSONObject().put("error",
								insertMigrationDetails.toString()).toString());
			}
			response.flushBuffer();
			response.getWriter().close();
		} catch (Exception e) {
			Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
					.entity("Cannot insert migration record "
							+ insertMigrationDetails.toString()
							+ e.getMessage()).build();
		}

	}

	@GET
	@RequestMapping(value = "/download/zip")
	@Produces("application/zip")
	public void downloadZippedFile(HttpServletRequest request,
			HttpServletResponse response) {
		// get the CTools site id
		Map<String, String[]> parameterMap = request.getParameterMap();
		String site_id = parameterMap.get("site_id")[0];
		log.info(site_id);

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		log.info(sessionId);
		if (sessionId != null) {
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
				try {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ZipOutputStream out = new ZipOutputStream(baos);

					// prepare zip entry for site content objects
					zipSiteContent(siteResourceJson, sessionId, out);

					out.flush();
					baos.flush();
					out.close();
					baos.close();
					zipContent = baos.toByteArray();
				} catch (IOException ee) {
					log.warn("downloadZippedFile: IOException of constructing zip file.");
				}

				if (zipContent != null) {
					//
					// Sends the response back to the user / browser. The
					// content for zip file type is "application/zip". We
					// also set the content disposition as attachment for
					// the browser to show a dialog that will let user
					// choose what action will he do to the sent content.
					//
					ServletOutputStream sos = response.getOutputStream();
					response.setContentType("application/zip");
					String zipFileName = site_id + "_content.zip";
					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					sos.write(zipContent);
					sos.flush();

					log.info(":downloadZippedFile end: successfully download zip file for site "
							+ site_id);
				} else {
					log.error("Problem download zip file for site " + site_id);
				}

			} catch (RestClientException e) {
				String errorMessage = "Cannot find site by siteId: " + site_id
						+ " " + e.getMessage();
				Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
						.type(MediaType.TEXT_PLAIN).build();
				log.error(errorMessage);
			} catch (IOException e) {
				String errorMessage = "Problem getting content zip file for "
						+ site_id + " " + e.getMessage();
				Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(errorMessage).type(MediaType.TEXT_PLAIN)
						.build();
				log.error(errorMessage);
			}
		}
	}

	/**
	 * create zip entry for folders and files
	 */
	private void zipSiteContent(String siteResourceJson, String sessionId,
			ZipOutputStream out) {
		// site root folder
		String rootFolderPath = null;

		JSONObject obj = new JSONObject(siteResourceJson);

		JSONArray array = obj
				.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

		for (int i = 0; i < array.length(); i++) {
			JSONObject contentItem = array.getJSONObject(i);

			// get only the url after "/access/" string
			String contentUrl = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_URL));
			contentUrl = contentUrl.substring(contentUrl
					.indexOf(CTOOLS_ACCESS_STRING)
					+ CTOOLS_ACCESS_STRING.length());

			// inside the JSON feed, the container string is of format
			// /content/<folder_url>
			// remote the prefix "/content"
			String container = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_CONTAINER));
			container = container.substring("/content".length());

			String type = contentItem.getString(CONTENT_JSON_ATTR_TYPE);
			String title = contentItem.getString(CONTENT_JSON_ATTR_TITLE);

			if (COLLECTION_TYPE.equals(type)) {
				// folders
				if (rootFolderPath == null) {
					rootFolderPath = contentUrl;
				} else {
					// create the zipentry for the sub-folder first
					String folderName = contentUrl.replace(rootFolderPath, "");
					ZipEntry folderEntry = new ZipEntry(folderName);
					try {
						out.putNextEntry(folderEntry);
					} catch (IOException e) {
						log.error(":zipSiteContent: problem closing zip entry "
								+ folderName + " " + e);
					}
				}

			} else {
				// files
				if (contentUrl == null && contentUrl.length() == 0) {
					// log error if the content url is missing
					log.error("No url for content " + title);
				} else if (container == null && container.length() == 0) {
					// log error if the content url is missing
					log.error("No container folder url for content " + title);
				} else {
					//
					// Call the zipFiles method for creating a zip stream.
					//
					String filePath = contentUrl.replace(rootFolderPath, "");
					zipFiles(filePath, contentUrl, sessionId, out);
				}
			}
		} // for
	}

	/**
	 * create zip entry for files
	 */
	private void zipFiles(String fileName, String fileUrl, String sessionId,
			ZipOutputStream out) {
		String contentString = "";
		try {
			Service service = new Service();
			Call nc = (Call) service.createCall();

			nc.setTargetEndpointAddress(env.getProperty("ctools.server.url")
					+ "sakai-axis/ContentHosting.jws");

			nc.removeAllParameters();
			nc.setOperationName(OPERATION_GET_CONTENT_DATA);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.setReturnType(XMLType.XSD_STRING);
			contentString = (String) nc.invoke(new Object[] { sessionId,
					fileUrl });

		} catch (Exception e) {
			log.error(":zipFiles " + fileUrl + " " + e.getMessage());
		}

		InputStream content = null;
		try {

			Base64.Decoder decoder = Base64.getDecoder();
			content = new ByteArrayInputStream(decoder.decode(contentString));

			int length = 0;
			byte data[] = new byte[1024 * 10];
			BufferedInputStream bContent = null;
			try {

				bContent = new BufferedInputStream(content);
				ZipEntry fileEntry = new ZipEntry(fileName);
				out.putNextEntry(fileEntry);
				int bCount = -1;
				while ((bCount = bContent.read(data)) != -1) {
					out.write(data, 0, bCount);
					length = length + bCount;
				}

				try {
					out.closeEntry(); // The zip entry need to be closed
				} catch (IOException ioException) {
					log.error(":zipFiles: problem closing zip entry "
							+ fileName + " " + ioException);
				}
			} catch (IllegalArgumentException iException) {
				log.warn(":zipFiles: problem creating BufferedInputStream with content and length "
						+ data.length + iException);
			} finally {
				if (bContent != null) {
					try {
						bContent.close(); // The BufferedInputStream needs to be
											// closed
					} catch (IOException ioException) {
						log.warn(":zipFiles: problem closing FileChannel "
								+ ioException);
					}
				}
			}
		} catch (IOException e) {
			log.warn(" zipFiles--IOException: : fileName=" + fileName);
		} finally {
			if (content != null) {
				try {
					content.close(); // The input stream needs to be closed
				} catch (IOException ioException) {
					log.warn(":zipFiles: problem closing Inputstream content "
							+ ioException);
				}
			}
		}
	}

	/**
	 * generate output for JSON_ready input value
	 */
	private void JSON_response(HttpServletResponse response, String jsonValue,
			String errorMessage, String requestUrl) {
		try {
			// output json
			response.setContentType(MediaType.APPLICATION_JSON);
			if (jsonValue == null) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().write(errorMessage);
			} else {
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().write(jsonValue);
			}
			response.getWriter().close();
		} catch (IOException e) {
			log.error(requestUrl + e.getMessage());
		}
	}

	/************* Box integration *****************/
	/**
	 * get json string og box folders
	 * 
	 * @return
	 */
	@RequestMapping("/box/folders")
	public void getBoxFolders(HttpServletRequest request,
			HttpServletResponse response) {

		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxClientRedirectUri = env.getProperty(BOX_CLIENT_REDIRECT_URL)
				+ "/box/folders_authorized";

		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null
				|| boxClientRedirectUri == null) {
			log.error("Missing box integration parameters");
			return;
		}
		String remoteUserEmail = request.getRemoteUser();

		// go to authentication screen
		String boxAPIUrl = env.getProperty("box_api_url");
		BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUri,
				remoteUserEmail, response);
	}

	@RequestMapping("/box/folders_authorized")
	@Produces(MediaType.APPLICATION_JSON)
	public List<HashMap<String, String>> getBoxFoldersAuthorized(
			HttpServletRequest request) {
		String rv = "";
		// get the authCode
		String authCode = BoxUtils.getAuthCodeFromBoxCallback(request);
		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);

		if (boxClientId == null || boxClientSecret == null || authCode == null) {
			log.error("Missing box integration parameters ");
			return null;
		}

		// make connection
		BoxAPIConnection api = new BoxAPIConnection(boxClientId,
				boxClientSecret, authCode);
		BoxUser.Info userInfo = BoxUser.getCurrentUser(api).getInfo();
		log.info("Box user log in success: user name = " + userInfo.getName()
				+ " user login = " + userInfo.getLogin());

		// get the root Box folder
		BoxFolder rootFolder = BoxFolder.getRootFolder(api);

		// get list of properties from all Box items contained
		List<HashMap<String, String>> folderItems = BoxUtils.listBoxFolders(
				null, api, rootFolder, "", 0);

		return folderItems;
	}
}
