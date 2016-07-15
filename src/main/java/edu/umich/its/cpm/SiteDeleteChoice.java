package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.UUID;

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
 * User can make choice to let CPM admins/tool remove their CTools project site
 * We store the user choice inside database
 * @author zqian
 *
 */

@Entity
public class SiteDeleteChoice {

	private static final Logger log = LoggerFactory.getLogger(SiteDeleteChoice.class);
	
	/**
	 * Primary key field
	 * the CTools site id
	 */
	@Id
	@Column(name = "SITE_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "SITE_DELETE_CHOICE")
	@Getter
	@Setter
	private String siteId;

	/**
	 * User id - who made the choice
	 */
	@Column(name = "USER_ID", columnDefinition = "VARCHAR(99) NOT NULL", table = "SITE_DELETE_CHOICE")
	@Getter
	@Setter
	private String userId;

	@Column(name = "CONSENT_TIME", columnDefinition = "TIMESTAMP NOT NULL", table = "SITE_DELETE_CHOICE")
	@Getter
	@Setter
	private Timestamp consentTime;

	protected SiteDeleteChoice() {
	}
	
	public SiteDeleteChoice(String siteId, String userId, Timestamp consentTime) {
		this.siteId = siteId;
		this.userId = userId;
		this.consentTime = consentTime;
	}

	@Override
	public String toString() {
		String s = String
				.format("SiteDeleteChoice[siteId=%s, "
						+ "userId='%s',"
						+ "consentTime='%s',"
						+ "]", siteId, userId, consentTime);
		return s;
	}
}
