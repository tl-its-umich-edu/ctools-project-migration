package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Created by pushyami on 1/19/17.
 */
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Component
public class StatusDependencyBox extends StatusDependencyCtools{

    private static final Logger log = LoggerFactory.getLogger(StatusDependencyBox.class);


    public String getId() {
        return Utils.STATUS_DEPENDENCIES_BOX;
    }

    public String invoke() {

        String boxURL = env.getProperty(Utils.BOX_API_URL);
        return Utils.dependencyStatus(boxURL);
    }
}
