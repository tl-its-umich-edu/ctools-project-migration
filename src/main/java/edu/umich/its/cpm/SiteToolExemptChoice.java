package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.UUID;

import javax.persistence.Table;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.SequenceGenerator;
import javax.persistence.Id;
import javax.persistence.GenerationType;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonRawValue;

/**
 * User can make choice to NOT allow CPM tool/admin to migrate their tools
 * @author zqian
 *
 */

@Entity
@Table(name = "SITE_TOOL_EXEMPT_CHOICE")
public class SiteToolExemptChoice {

	private static final Logger log = LoggerFactory.getLogger(SiteToolExemptChoice.class);
	
	/**
	 * Primary key field
	 */
	@Id
	@Column(name = "EXEMPT_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String exemptId;
	
	/**
	 * CTools site id
	 */
	@Column(name = "SITE_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String siteId;
	
	/**
	 * CTools tool id
	 */
	@Column(name = "TOOL_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String toolId;
	
	/**
	 * CTools tool type: Resources or Email Archive
	 */
	@Column(name = "TOOL_NAME", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String toolName;

	/**
	 * User id - who made the choice
	 */
	@Column(name = "USER_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String userId;

	@Column(name = "CONSENT_TIME", columnDefinition = "TIMESTAMP NOT NULL")
	@Getter
	@Setter
	private Timestamp consentTime;

	protected SiteToolExemptChoice() {
	}
	
	public SiteToolExemptChoice(String siteId, String toolId, String toolName, String userId, Timestamp consentTime) {
		this.exemptId = UUID.randomUUID().toString();
		this.siteId = siteId;
		this.toolId = toolId;
		this.toolName = toolName;
		this.userId = userId;
		this.consentTime = consentTime;
	}

	@Override
	public String toString() {
		String s = String
				.format("SiteDeleteChoice[exemptId=%s,"
						+ "siteId=%s, "
						+ "toolId=%s, "
						+ "toolName=%s, "
						+ "userId='%s',"
						+ "consentTime='%s',"
						+ "]", exemptId, siteId, toolId, toolName, userId, consentTime);
		return s;
	}
}
