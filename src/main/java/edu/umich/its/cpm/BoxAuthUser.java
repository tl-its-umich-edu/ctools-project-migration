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

@Entity
@Table(name = "BOX_AUTH_USER")
public class BoxAuthUser {

	private static final Logger log = LoggerFactory.getLogger(BoxAuthUser.class);
	
	/**
	 * Primary key field
	 * the CoSign user id
	 */
	@Id
	@Column(name = "USER_ID", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String userId;

	/**
	 * state 
	 * An arbitrary string of your choosing that will be included in the response 
	 * to your application. Anything that might be useful for your application can 
	 * be included. Box roundtrips this information back to your application, 
	 * and strongly recommends that you include an anti-forgery token, 
	 * and confirm it in the response to prevent CSRF attacks to your users.
	 * null for self-migrated (non-batch) site migration
	 */
	@Column(name = "STATE", columnDefinition = "VARCHAR(99)")
	@Getter
	@Setter
	private String state;

	/**
	 * The access_token is the actual string needed to make API requests. 
	 * Each access_token is valid for 1 hour. 
	 * In order to get a new, valid token, you can use the accompanying refresh_token. 
	 * Each refresh_token is valid for one use in 60 days. 
	 * Every time you get a new access_token by using a refresh_token, 
	 * Box reset your timer for the 60 day period and hand you a new refresh_token. 
	 */
	@Column(name = "ACCESS_TOKEN", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String accessToken;

	@Column(name = "REFRESH_TOKEN", columnDefinition = "VARCHAR(99) NOT NULL")
	@Getter
	@Setter
	private String refreshToken;

	protected BoxAuthUser() {
	}
	
	public BoxAuthUser(String userId, String state, 
			String accessToken, String refreshToken) {
		this.userId = userId;
		this.state = state;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
	}

	@Override
	public String toString() {
		String s = String
				.format("BoxAuthUser[id=%d, "
						+ "userId='%s',"
						+ "state='%s',"
						+ "accessToken='%s',"
						+ "refreshToken='%s',"
						+ "]", userId, state, accessToken, refreshToken);
		return s;
	}
}
