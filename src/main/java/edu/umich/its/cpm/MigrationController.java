package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.text.SimpleDateFormat;

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

import org.json.JSONObject;
import org.json.JSONArray;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;

import com.google.gson.Gson;

@RestController
public class MigrationController {

	private static final SimpleDateFormat date_formatter = new SimpleDateFormat(
			"yyyy-MM-dd-hh.mm.ss");

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	private final AtomicLong counter = new AtomicLong();

	@Autowired
	MigrationRepository repository;

	@Autowired
	private Environment env;

	/**
	 * get all CTools sites where user have site.upd permission
	 * 
	 * @return
	 */
	@RequestMapping("/projects")
	public String getProjectSites(HttpServletRequest request) {
		String rv = "";

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/withPerm/.json?permission=site.upd"
			String requestUrl = env.getProperty("ctools.direct.url")
					+ "site/withPerm/.json?permission=site.upd&_sessionId="
					+ sessionId;
			log.info(requestUrl);
			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
			}
		}
		return rv;
	}

	/**
	 * get page information
	 * 
	 * @param site_id
	 * @return
	 */
	@RequestMapping("/projects/{site_id}")
	public String getProjectSitePages(@PathVariable String site_id,
			HttpServletRequest request) {
		String rv = "";

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.direct.url") + "site/"
					+ site_id + "/pages.json?_sessionId=" + sessionId;
			log.info(requestUrl);

			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
				rv = "Cannot find site by siteId: " + site_id;
			}
		}
		return rv;
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
		String requestUrl = env.getProperty("ctools.direct.url")
				+ "session?_username=" + env.getProperty("username")
				+ "&_password=" + env.getProperty("password");
		log.info(requestUrl);
		ResponseEntity<String> response = restTemplate.postForEntity(
				requestUrl, null, String.class);
		HttpStatus status = response.getStatusCode();

		if (!status.equals(HttpStatus.CREATED)) {
			// return error if a new CTools session could not be created using
			// username and password provided
			log.info("Wrong user id or password. Cannot login to CTools "
					+ env.getProperty("ctools.direct.url"));
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
			requestUrl = env.getProperty("ctools.direct.url")
					+ "session/becomeuser/" + remoteUser + ".json?_sessionId="
					+ sessionId;
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
	@RequestMapping("/migrations")
	public String migrations() {
		return JSONObject.valueToString(repository.findAll());
	}

	/**
	 * get a specific migration record
	 * 
	 * @param migration_id
	 * @return
	 */
	@RequestMapping("/migrations/{migration_id}")
	public String migrations(@PathVariable("migration_id") Integer migration_id) {
		Object o = repository.findOne(migration_id);
		if (o != null) {
			// find migration record with id
			return JSONObject.valueToString(o);
		} else {
			// no such migration record
			return "no matching record for /migrations/" + migration_id;
		}
	}

	/**
	 * found all migrated records (where the migration record have "end" field
	 * value
	 * 
	 * @return
	 */
	@RequestMapping("/migrated")
	public String migrated() {
		return JSONObject.valueToString(repository.findMigrated());
	}

	/**
	 * insert a new record of Migration
	 * 
	 * @param request
	 */
	@RequestMapping(value = "/migration", method = RequestMethod.POST)
	public Migration migrated(HttpServletRequest request) {
		Map<String, String[]> parameterMap = request.getParameterMap();
		Migration m = new Migration(parameterMap.get("site_id")[0],
				parameterMap.get("site_name")[0],
				parameterMap.get("tool_id")[0],
				parameterMap.get("tool_name")[0], request.getRemoteUser(),
				new java.sql.Timestamp(System.currentTimeMillis()), // start time is now
				null, // no end time
				parameterMap.get("destination_type")[0], null);
		Migration rv = null;
		try {
			rv = repository.save(m);
		} catch (Exception e) {
			StringBuffer buffer = new StringBuffer();
			buffer.append("Exception saving migration record site_id=")
					.append(parameterMap.get("site_id")[0])
					.append(" site_name=")
					.append(parameterMap.get("site_name")[0])
					.append(" tool_id=").append(parameterMap.get("tool_id")[0])
					.append(" tool_name=")
					.append(parameterMap.get("tool_name")[0])
					.append(" migrated_by=").append(request.getRemoteUser())
					.append(" destination_type=")
					.append(parameterMap.get("destination_type")[0])
					.append(" \n ").append(e.getMessage());
			log.error(buffer.toString());
		}
		return rv;
	}

	/************* Box integration *****************/
	/**
	 * get json string og box folders
	 * 
	 * @return
	 */
	private String DEVELOPER_TOKEN = "";
	private static final int MAX_DEPTH = 1;

	@RequestMapping("/box/folders")
	public String getBoxFolders() {

		String rv = "";

		// TODO: get the DEVELOPER_TOKEN from configuratio for now, will do
		// token auto-generation in the future
		DEVELOPER_TOKEN = env.getProperty("box_api_token");

		// log in
		BoxAPIConnection api = new BoxAPIConnection(DEVELOPER_TOKEN);
		BoxUser.Info userInfo = BoxUser.getCurrentUser(api).getInfo();
		log.info("Box user log in success: user name = " + userInfo.getName()
				+ " user login = " + userInfo.getLogin());

		// get the root Box folder
		BoxFolder rootFolder = BoxFolder.getRootFolder(api);

		// get list of properties from all Box items contained
		List<HashMap<String, String>> folderItems = listFolderAndFile(null,
				api, rootFolder, "");

		// construct JSON
		Gson gson = new Gson();
		rv = gson.toJson(folderItems);

		return rv;
	}

	/**
	 * recursively get all file and folder items inside the root folder
	 */
	public List<HashMap<String, String>> listFolderAndFile(
			List<HashMap<String, String>> rv, BoxAPIConnection api,
			BoxFolder folder, String folderPath) {
		if (rv == null)
			rv = new ArrayList<HashMap<String, String>>();

		for (BoxItem.Info itemInfo : folder) {

			if (itemInfo instanceof BoxFile.Info) {

				BoxFile.Info fileInfo = (BoxFile.Info) itemInfo;
				BoxFile file = new BoxFile(api, fileInfo.getID());

				// construct path
				String filePath = folderPath + "/" + fileInfo.getName();

				// get item properties
				rv.add(getBoxItemProperties(file.getInfo(), filePath));

			} else if (itemInfo instanceof BoxFolder.Info) {

				BoxFolder.Info folderInfo = (BoxFolder.Info) itemInfo;
				BoxFolder xfolder = new BoxFolder(api, folderInfo.getID());
				String currentFolderPath = folderPath + "/"
						+ folderInfo.getName();
				rv.add(getBoxItemProperties(xfolder.getInfo(),
						currentFolderPath));
				listFolderAndFile(rv, api, xfolder, currentFolderPath);
			}
		}

		return rv;

	}

	/**
	 * get BoxItem properties
	 */
	private HashMap<String, String> getBoxItemProperties(BoxItem.Info info,
			String path) {
		HashMap<String, String> rv = new HashMap<String, String>();
		rv.put("name", info.getName());
		rv.put("size", String.valueOf(info.getSize()));

		// type defaults to folder
		String type = "folder";
		if (info instanceof BoxFile.Info) {
			rv.put("sh1", ((BoxFile.Info) info).getSha1());
			type = "file";
		}
		rv.put("type", type);

		rv.put("content_created_at", format_date(info.getContentCreatedAt()));
		rv.put("content_modified_at", format_date(info.getContentModifiedAt()));
		rv.put("created_at", format_date(info.getCreatedAt()));
		rv.put("created_by", get_box_user_name(info.getCreatedBy()));
		rv.put("modified_by", get_box_user_name(info.getCreatedBy()));
		rv.put("owned_by", get_box_user_name(info.getOwnedBy()));
		rv.put("description", info.getDescription());
		rv.put("path", path);
		return rv;
	}

	/**
	 * a function to format Date object
	 */
	private String format_date(Date date) {
		String rv = "";
		if (date != null) {
			rv = date_formatter.format(date);
		}
		return rv;
	}

	/**
	 * return the name of BoxUser
	 */
	private String get_box_user_name(BoxUser.Info uInfo) {
		String rv = "";
		if (uInfo != null) {
			rv = uInfo.getName();
		}
		return rv;
	}
}
