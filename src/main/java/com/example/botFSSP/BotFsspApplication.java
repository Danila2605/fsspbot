package com.example.botFSSP;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BotFsspApplication {

	public static void main(String[] args) {
		SpringApplication.run(BotFsspApplication.class, args);
	}

}
