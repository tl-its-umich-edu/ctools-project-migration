package edu.umich.its.cpm;

/**
 * Created by pushyami on 10/3/16.
 */
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StatusReport {
    private String id;
    private String msg;
    private String status;
    private List<String> allAttachments = new ArrayList<String>();
    private List<String> failedAttachments=new ArrayList<String>();

    public List<String> getFailedAttachments() {
        return failedAttachments;
    }

    public void setFailedAttachments(List<String> failedAttachments) {
        this.failedAttachments = failedAttachments;
    }

    public List<String> getAllAttachments() {
        return allAttachments;
    }

    public void setAllAttachments(List<String> allAttachments) {
        this.allAttachments = allAttachments;
    }


    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public JSONObject getJsonReportObject(){
        JSONObject status = new JSONObject();
        status.put(Utils.REPORT_ATTR_ITEM_ID,getId());
        status.put(Utils.REPORT_ATTR_ITEM_STATUS,getStatus());
        status.put(Utils.REPORT_ATTR_MESSAGE,getMsg());
        return status;
    }

    public void addAllAttachmnts(String attachmentName){
        allAttachments.add(attachmentName);

    }
    public void addFailedAttachments(String failedAttachName){
        failedAttachments.add(failedAttachName);
    }





}
