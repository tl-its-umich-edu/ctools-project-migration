package edu.umich.its.cpm;

/**
 * Created by pushyami on 10/3/16.
 */

public class MailResultPair {

    private StatusReport report;

    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StatusReport getReport() {
        return report;
    }

    public void setReport(StatusReport report) {
        this.report = report;
    }


    MailResultPair(StatusReport report, String message){
        this.report =report;
        this.message=message;
    }


}
