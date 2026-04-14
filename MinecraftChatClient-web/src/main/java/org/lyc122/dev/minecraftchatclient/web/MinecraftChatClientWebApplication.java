package org.lyc122.dev.minecraftchatclient.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories("org.lyc122.dev.minecraftchatclient.web.repository")
@EnableScheduling
public class MinecraftChatClientWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinecraftChatClientWebApplication.class, args);
    }
}
