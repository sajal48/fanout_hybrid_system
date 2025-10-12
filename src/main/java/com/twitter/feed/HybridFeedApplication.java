package com.twitter.feed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for the Hybrid Feed System.
 *
 * This application implements a Twitter-like feed system with a hybrid fanout strategy
 * that efficiently handles both regular users (< 10K followers) and celebrity users
 * (>= 10K followers) using different fanout approaches.
 *
 * @author Hybrid Feed System Team
 * @version 1.0.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories(basePackages = "com.twitter.feed")
public class HybridFeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(HybridFeedApplication.class, args);
    }
}
