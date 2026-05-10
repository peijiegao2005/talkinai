package com.talkingai.soulchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SoulchatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoulchatApplication.class, args);
    }

}
