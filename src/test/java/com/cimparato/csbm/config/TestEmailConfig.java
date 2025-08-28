package com.cimparato.csbm.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@TestConfiguration
public class TestEmailConfig {

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }
}
