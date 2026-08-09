package com.v2board.api.queue;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(V2boardQueueProperties.class)
public class JobQueueConfig {
}
