package com.example.cargotracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * A 社国際貨物輸送管理システム（cargo-tracker）。境界づけられたコンテキストをモジュールに分けたモジュラーモノリス（ADR-001）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CargoTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CargoTrackerApplication.class, args);
	}

}
