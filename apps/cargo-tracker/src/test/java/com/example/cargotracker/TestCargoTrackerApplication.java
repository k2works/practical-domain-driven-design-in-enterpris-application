package com.example.cargotracker;

import org.springframework.boot.SpringApplication;

public class TestCargoTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(CargoTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
