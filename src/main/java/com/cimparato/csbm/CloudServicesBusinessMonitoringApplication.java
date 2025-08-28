package com.cimparato.csbm;

import com.cimparato.csbm.config.properties.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CloudServicesBusinessMonitoringApplication {

	private static final Logger log = LoggerFactory.getLogger(CloudServicesBusinessMonitoringApplication.class);

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(CloudServicesBusinessMonitoringApplication.class, args);

		String[] activeProfiles = context.getEnvironment().getActiveProfiles();
		log.info("Active profiles: {}", Arrays.toString(activeProfiles));

		String issuerUri = context.getEnvironment().getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
		log.info("Keycloak issuer URI: {}", issuerUri);
	}

}
