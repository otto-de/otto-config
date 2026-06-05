package de.otto.config.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {"de.otto.config", "de.otto.search"})
public class DemoApplication {
    
    public static void main(String[] args) {
        final SpringApplication springApplication = new SpringApplication(DemoApplication.class);
        springApplication.run(args);
    }
}
