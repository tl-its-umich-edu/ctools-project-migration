package edu.umich.its.cpm;

import java.util.Map;

/**
 * Created by pushyami on 1/30/17.
 */
public class ErrorJson {

    public Integer status;
    public String error;
    public String message;
    public String path;

    public ErrorJson(int status, Map<String, Object> errorAttributes) {
        this.status = status;
        this.error = (String) errorAttributes.get("error");
        this.message = (String) errorAttributes.get("message");
        this.path = (String)errorAttributes.get("path");
    }
}
