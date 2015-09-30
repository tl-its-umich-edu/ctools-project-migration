package edu.umich.its.cpm;


import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import java.sql.Timestamp;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.GenerationType;

@Entity
public class Migration {
  @Id
  @GeneratedValue(strategy=GenerationType.IDENTITY)
  @Column(name="ID",
  table="MIGRATION")
  private Integer id;
  
  @Column(name="SITE_ID",
  columnDefinition="VARCHAR(99) NOT NULL",
  table="MIGRATION")
  private String siteId; 
  
  @Column(name="SITE_OWNER",
  columnDefinition="VARCHAR(99) NOT NULL",
  table="MIGRATION")
  private String siteOwner;
  
  @Column(name="MIGRATED_BY",
  columnDefinition="VARCHAR(99) NOT NULL",
  table="MIGRATION")
  private String migratedBy;
  
  @Column(name="START_TIME",
  columnDefinition="TIMESTAMP",
  table="MIGRATION")
  private Timestamp startTime;
  
  @Column(name="STOP_TIME",
  columnDefinition="TIMESTAMP",
  table="MIGRATION")
  private Timestamp stopTime;
  
  @Column(name="TOOL",
  columnDefinition="VARCHAR(99) NOT NULL",
  table="MIGRATION")
  private String tool;
  
  @Column(name="DESTINATION_TYPE",
  columnDefinition="VARCHAR(99) NOT NULL",
  table="MIGRATION")
  private String destinationType;
  
  @Column(name="DESTINATION_URL",
  columnDefinition="VARCHAR(99)",
  table="MIGRATION")
  private String destinationUrl;

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

	public String getSiteOwner() {
		return siteOwner;
	}

	public void setSiteOwner(String siteOwner) {
		this.siteOwner = siteOwner;
	}

	public String getMigratedBy() {
		return migratedBy;
	}

	public void setMigratedBy(String migratedBy) {
		this.migratedBy = migratedBy;
	}

	public Timestamp getStartTime() {
		return startTime;
	}

	public void setStartTime(Timestamp startTime) {
		this.startTime = startTime;
	}

	public Timestamp getStopTime() {
		return stopTime;
	}

	public void setStopTime(Timestamp stopTime) {
		this.stopTime = stopTime;
	}

	public String getTool() {
		return tool;
	}

	public void setTool(String tool) {
		this.tool = tool;
	}

	public String getDestination_type() {
		return destinationType;
	}

	public void setDestinationType(String destinationType) {
		this.destinationType = destinationType;
	}

	public String getDestinationUrl() {
		return destinationUrl;
	}

	public void setDestinationUrl(String destinationUrl) {
		this.destinationUrl = destinationUrl;
	}
  
	protected Migration() {}

	public Migration(String siteId,
		  			String siteOwner,
		  			String migratedBy,
		  			Timestamp startTime,
		  			Timestamp stopTime,
		  			String tool,
		  			String destinationType,
		  			String destinationUrl) {
		this.siteId = siteId;
		this.siteOwner = siteOwner;
		this.migratedBy = migratedBy;
		this.startTime = startTime;
		this.stopTime = stopTime;
		this.tool = tool;
		this.destinationType = destinationType;
		this.destinationUrl = destinationUrl;
	}

	@Override
	public String toString() {
		return String.format(
					"Migration[id=%d, "
					+ "site_id='%s',"
					+ "site_owner='%s',"
					+ "migrated_by='%s',"
					+ "start_time='%s',"
					+ "stop_time='%s',"
					+ "tool='%s',"
					+ "destination_type='%s',"
					+ "destination_url='%s'"
					+ "]",
					id, siteId,siteOwner, migratedBy, startTime, stopTime, tool, destinationType, destinationUrl);
	}
}
