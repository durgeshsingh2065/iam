package com.iam.assistant.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class McpAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpAgentApplication.class, args);
    }
}
