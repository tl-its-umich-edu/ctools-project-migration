package edu.umich.its.cpm;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.json.JSONObject;

/**
 * this is to add a new status end point with application version
 * information, and link to external dependencies
 * 
 * @author zqian
 *
 */
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Component
class StatusEndpoint implements Endpoint<String>, ServletContextAware{

	private static ServletContext servletContext = null;
	
	@Autowired
	private Environment env;
	
	public String getId() {
		return Utils.MIGRATION_STATUS;
	}

	public boolean isEnabled() {
		return true;
	}

	public boolean isSensitive() {
		return true;
	}

	public String invoke() {
		String rv = "";
		
		// Custom logic to build the output
		try {
			// maven build has put the git version information into MANIFEST.MF FILE
			Properties props = new Properties();
			props.load(servletContext.getResourceAsStream("/META-INF/MANIFEST.MF"));
			// output the git version, CTools and Box url 
			HashMap<String, Object> statusMap = new HashMap<String, Object>();
			// all information related to GIT build
			HashMap<String, Object> buildMap = new HashMap<String, Object>();
			buildMap.put("project", "CTool Project Migration");
			buildMap.put("GIT URL", (String) props.get("git_url"));
			buildMap.put("GIT branch", (String) props.get("git_branch"));
			buildMap.put("GIT commit", (String) props.get("git_commit"));
			buildMap.put("Jenkins Build ID", (String) props.get("build_id"));
			buildMap.put("Jenkins Build Number", (String) props.get("build_number"));
			buildMap.put("Jenkins Build URL", (String) props.get("build_url"));
			buildMap.put("Jenkins Build Tag", (String) props.get("build_tag"));
			
			statusMap.put("build", buildMap);
			// all external links
			HashMap<String, Object> urlMap = new HashMap<String, Object>();
			urlMap.put("ping", env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)+"/status/ping.json");
			urlMap.put("CTools server", env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL));
			urlMap.put("Box server", env.getProperty("box_api_url"));
			statusMap.put("urls", urlMap);
			rv = (new JSONObject(statusMap)).toString();
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		return 	rv;
	}
	
	public void setServletContext(ServletContext servletContext){
		this.servletContext = servletContext;
	}
	
}