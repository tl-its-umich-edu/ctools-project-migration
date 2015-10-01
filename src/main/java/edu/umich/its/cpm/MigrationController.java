package edu.umich.its.cpm;


import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.gson.Gson;

@RestController
public class MigrationController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();
    
    @Autowired MigrationRepository repository;

    @RequestMapping("/migrations")
    public String migrations() {
    	Gson gson = new Gson();
    	return gson.toJson(repository.findAll());
    }
    
    @RequestMapping("/migrated")
    public String migrated() {
    	Gson gson = new Gson();
    	return gson.toJson(repository.find());
    }
}