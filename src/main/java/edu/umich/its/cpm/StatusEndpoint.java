package edu.umich.its.cpm;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;

/**
 * this is to add a new status end point with application version
 * information, and link to external dependencies
 * 
 * @author zqian
 *
 */
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Component
public class StatusEndpoint implements Endpoint<List<String>>, ServletContextAware{

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

	public List<String> invoke() {
		// Custom logic to build the output
		List<String> messages = new ArrayList<String>();
		messages.add("The status page:");
		
		try {
			// maven build has put the git version information into MANIFEST.MF FILE
			String name = "/META-INF/MANIFEST.MF";
			Properties props = new Properties();
			props.load(servletContext.getResourceAsStream(name));
			// output the git version, CTools and Box url 
			messages.add("GIT version: " + (String) props.get("git-SHA-1"));
			messages.add("CTools server: " + env.getProperty("ctools.server.url"));
			messages.add("Box server:" + env.getProperty("box_api_url"));
			// use the spring-boot's default "/health" endpoint for /status/ping purpose
			messages.add("Link to status ping page :" + env.getProperty("server_url") + "/health");
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		return messages;
	}
	
	public void setServletContext(ServletContext servletContext){
		this.servletContext = servletContext;
	}
	
}