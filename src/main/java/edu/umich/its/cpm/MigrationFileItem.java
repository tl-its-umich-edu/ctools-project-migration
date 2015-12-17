package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

public class MigrationFileItem {

	private static final Logger log = LoggerFactory.getLogger(Migration.class);
	
	@Getter
	@Setter
	private String path;

	@Getter
	@Setter
	private String file_name;

	@Getter
	@Setter
	private String status;

	protected MigrationFileItem() {
	}

	public MigrationFileItem(String path, String file_name, String status) {
		this.path = path;
		this.file_name = file_name;
		this.status = status;
	}

}
