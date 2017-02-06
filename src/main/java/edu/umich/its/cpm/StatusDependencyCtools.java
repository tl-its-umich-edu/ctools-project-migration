package edu.umich.its.cpm;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Created by pushyami on 1/19/17.
 */
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Component
public class StatusDependencyCtools implements Endpoint<String> {

    private static final Logger log = LoggerFactory.getLogger(StatusDependencyCtools.class);

    @Autowired
    protected Environment env;

    public String getId() {
        return Utils.STATUS_DEPENDENCIES_CTOOLS;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean isSensitive() {
        return true;
    }

    public String invoke() {
        String ctoolsURL = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)+"access/content/public/ok.txt";
        return Utils.dependencyStatus(ctoolsURL);
    }
}
