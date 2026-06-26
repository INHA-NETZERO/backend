package com.netzero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NetzeroApplication {

	public static void main(String[] args) {
		SpringApplication.run(NetzeroApplication.class, args);
	}

}
