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
public class StatusEndpoint implements Endpoint<String>, ServletContextAware{

	private static ServletContext servletContext = null;
	
	@Autowired
	private Environment env;
	
	public String getId() {
		return "status";
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
			statusMap.put("project", "CTool Project Migration");
			statusMap.put("GIT repo", (String) props.get("git-repo"));
			statusMap.put("GIT version", (String) props.get("git-SHA-1"));
			statusMap.put("GIT timestamp", (String) props.get("git-timestamp"));
			statusMap.put("GIT branch name", (String) props.get("git-branch"));
			statusMap.put("CTools server", env.getProperty("ctools.server.url"));
			statusMap.put("Box server", env.getProperty("box_api_url"));
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