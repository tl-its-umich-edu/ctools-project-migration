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

}