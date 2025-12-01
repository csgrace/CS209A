package com.example.mvcdemo;

import com.example.mvcdemo.service.StudentService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MvcdemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MvcdemoApplication.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(StudentService service){
        return args -> {
            service.addStudents();
        };
    }

}
