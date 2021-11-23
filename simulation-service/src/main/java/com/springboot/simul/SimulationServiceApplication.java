package com.springboot.simul;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootApplication
@EnableWebMvc
public class SimulationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimulationServiceApplication.class, args);
	}

}
