package com.andesstay.ms_andesstay_bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsAndesstayBffApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsAndesstayBffApplication.class, args);
	}

}
