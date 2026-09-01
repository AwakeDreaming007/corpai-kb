package com.xufg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Langchain4jJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(Langchain4jJavaApplication.class, args);
    }

}
