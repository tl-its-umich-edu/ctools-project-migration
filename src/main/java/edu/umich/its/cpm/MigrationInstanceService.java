package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Service
@Component
public class MigrationInstanceService{

	private static final Logger log = LoggerFactory
			.getLogger(MigrationInstanceService.class);
	
	@Autowired
	MigrationTaskService migrationTaskService;
	
	public void createDownloadZipInstance(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String migrationId, MigrationRepository repository) throws InterruptedException {
		StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip");
		
        // async call
		migrationTaskService.downloadZippedFile(env, request, response, userId, sessionAttributes, siteId, migrationId, repository);
		stopWatch.stop();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip " + stopWatch.prettyPrint());
		
	}

	/*************** Box Migration ********************/
	@Async
	public Future<String> createUploadBoxInstance(Environment env, HttpServletRequest request,
			HttpServletResponse response, String userId, HashMap<String, Object> sessionAttributes, String siteId, String boxFolderId, String migrationId, MigrationRepository repository) throws InterruptedException {
		
		StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=box");
		
		String rv = "";
		List<Future<String>> futureList = new ArrayList<Future<String>>();
		futureList.add( migrationTaskService.uploadToBox(env, request, response, userId, sessionAttributes, siteId, boxFolderId, migrationId, repository));
 
        for (Future<String> future : futureList) {
            try {
                rv = future.get();
            } catch (java.util.concurrent.ExecutionException e)
            {
            	log.error(this + ":createUploadBoxInstance ", e);
            } catch (Exception e) {
            	log.error(this + ":createUploadBoxInstance ", e);
            }
        }
        
        stopWatch.stop();
        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=box " + stopWatch.prettyPrint());
		
        
        return  new AsyncResult<String>("success");
	}
}