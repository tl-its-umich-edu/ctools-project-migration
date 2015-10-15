package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.sql.Timestamp;

@SpringBootApplication
public class Application {

	private static final Logger log = LoggerFactory
			.getLogger(Application.class);

	public static void main(String[] args) {
		SpringApplication.run(Application.class);
	}

	@Bean
	public CommandLineRunner demo(MigrationRepository repository) {
		return (args) -> {
			// save a couple of Migrations
			repository.save(new Migration("site1", "site1 name",
					"sakai.resource", "Resource", "Jack", null, null, "Box",
					null));
			repository.save(new Migration("site1", "site1 name",
					"sakai.resource", "Resource", "Jane", null, null, "Box",
					null));

			// fetch all Migrations
			log.info("Migrations found with findAll():");
			log.info("-------------------------------");
			for (Migration Migration : repository.findAll()) {
				log.info(Migration.toString());
			}
			log.info("");

			// fetch an individual Migration by ID
			Migration Migration = repository.findOne(1);
			log.info("Migration found with findOne(1):");
			log.info("--------------------------------");
			log.info(Migration.toString());
			log.info("");
		};
	}

}