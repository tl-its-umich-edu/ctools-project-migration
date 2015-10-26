package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

/**
 * provides util functions for accessing Box APIs
 * 
 * @author zqian
 *
 */
public class BoxUtils {

	private static final int MAX_DEPTH = 0;
	
	private static final SimpleDateFormat date_formatter = new SimpleDateFormat(
			"yyyy-MM-dd-hh.mm.ss");
	
	private static final Logger log = LoggerFactory.getLogger(BoxUtils.class);

	public static void authenticate(String boxAPIUrl, String boxClientId,
			String boxClientRedirectUri, String remoteUserEmail,
			HttpServletResponse response) {
		// Box authorization
		RestTemplate restTemplate = new RestTemplate();
		
		if (boxAPIUrl == null)
		{
			// log error
			log.error("No Box API url specified. ");
			return;
		}
		
		String requestUrl = boxAPIUrl + "/oauth2/authorize"
				+ "?response_type=code" + "&client_id=" + boxClientId
				+ "&redirect_uri=" + boxClientRedirectUri
				+ "&state=&box_login=" + remoteUserEmail;
		log.info(requestUrl);

		try {
			String resultString = restTemplate.getForObject(requestUrl,
					String.class);

			// open window with resultString
			response.setContentType("text/html");
			response.getWriter().println(resultString);
			response.flushBuffer();
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
		} catch (IOException e) {
			log.error(requestUrl + e.getMessage());
		}
	}

	/**
	 * get the authCode as embedded from Box callback response
	 */
	public static String getAuthCodeFromBoxCallback(HttpServletRequest request) {
		// get the code String from Box authorization callback
		String authCode = "";
		java.util.Enumeration<java.lang.String> e = request.getParameterNames();
		while (e.hasMoreElements()) {
			String paramName = e.nextElement();
			if ("code".equals(paramName)) {
				authCode = request.getParameter(paramName);
				log.info("authCode:" + authCode);
				break;
			}
		}

		return authCode;
	}
	
	/**
	 * recursively get all file and folder items inside the root folder
	 */
	public static List<HashMap<String, String>> listBoxFolders(
			List<HashMap<String, String>> rv, BoxAPIConnection api,
			BoxFolder folder, String folderPath, int folderDepth) {
		if (rv == null)
			rv = new ArrayList<HashMap<String, String>>();

		for (BoxItem.Info itemInfo : folder) {
			if (itemInfo instanceof BoxFolder.Info) {

				BoxFolder.Info folderInfo = (BoxFolder.Info) itemInfo;
				BoxFolder xfolder = new BoxFolder(api, folderInfo.getID());
				String currentFolderPath = folderPath + "/" + folderInfo.getName();
				rv.add(getBoxItemProperties(xfolder.getInfo(),
						currentFolderPath));
				if (folderDepth < MAX_DEPTH)
				{
					// go one level deeper in folder structure
					listBoxFolders(rv, api, xfolder, currentFolderPath, folderDepth+1);
				}
			}
		}

		return rv;

	}

	/**
	 * get BoxItem properties
	 */
	private static HashMap<String, String> getBoxItemProperties(BoxItem.Info info, String path) {
		HashMap<String, String> rv = new HashMap<String, String>();
		rv.put("ID", info.getID());
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
	private static String format_date(Date date) {
		String rv = "";
		if (date != null) {
			rv = date_formatter.format(date);
		}
		return rv;
	}

	/**
	 * return the name of BoxUser
	 */
	private static String get_box_user_name(BoxUser.Info uInfo) {
		String rv = "";
		if (uInfo != null) {
			rv = uInfo.getName();
		}
		return rv;
	}
}