package edu.umich.its.cpm;


import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;

import com.google.gson.Gson;

@RestController
public class MigrationController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();
    
    @Autowired MigrationRepository repository;
    
    @RequestMapping("/projects")
    public String projects() {
    	Gson gson = new Gson();
    	return gson.toJson("to be implemented");
    }
    
    @RequestMapping("/projects/{site_id}")
    public String projects(@PathVariable("site_id") String migration_id) {
    	Gson gson = new Gson();
    	return gson.toJson("to be implemented");
    }

    /**
     * get all migration records
     * @return
     */
    @RequestMapping("/migrations")
    public String migrations() {
    	Gson gson = new Gson();
    	return gson.toJson(repository.findAll());
    }
    
    /**
     * get a specific migration record
     * @param migration_id
     * @return
     */
    @RequestMapping("/migration/{migration_id}")
    public String migration(@PathVariable("migration_id") Integer migration_id) {
    	Gson gson = new Gson();
    	return gson.toJson(repository.findOne(migration_id));
    }
    
    /**
     * found all migraged records
     * @return
     */
    @RequestMapping("/migrated")
    public String migrated() {
    	Gson gson = new Gson();
    	return gson.toJson(repository.findMigrated());
    }
    
    /**
     * insert a new record of Migration
     * @param request
     */
    @RequestMapping(value="/migration", method = RequestMethod.POST)
    public void migrated(HttpServletRequest request) {
	    Map<String, String[]> parameterMap = request.getParameterMap();
	    Migration m = new Migration(parameterMap.get("site_id")[0],
	    		parameterMap.get("site_name")[0],
	    		parameterMap.get("tool_id")[0],
	    		parameterMap.get("tool_name")[0],
	    		parameterMap.get("migrated_by")[0],
	    		new java.sql.Timestamp(System.currentTimeMillis()), /* start time is now*/
				null, /* no end time */
				parameterMap.get("destination_type")[0],
				null);
	    repository.save(m);
    }
}