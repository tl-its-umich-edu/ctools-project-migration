package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import javax.annotation.PostConstruct;

@SpringBootApplication
@EnableAsync
public class Application extends SpringBootServletInitializer {

	private static final Logger log = LoggerFactory
			.getLogger(Application.class);

	@Autowired
	private MigrationInstanceService migrationInstanceService;

	@Override
	protected SpringApplicationBuilder configure(
			SpringApplicationBuilder application) {
		return application.sources(Application.class);
	}

	@PostConstruct
	public void startMigrationProcessingThread() {

		try {
			migrationInstanceService.runProcessingThread();
			log.info(this + " startMigrationProcessingThread started.");
		} catch (InterruptedException e) {
			log.error(this
					+ "config error with migrationInstanceService.runProcessingThread() "
					+ e.getMessage());
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}