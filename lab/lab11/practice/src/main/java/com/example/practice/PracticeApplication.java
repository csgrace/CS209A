package com.example.practice;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.example.practice.model.Student;
import com.example.practice.repository.StudentRepository;

@SpringBootApplication
public class PracticeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PracticeApplication.class, args);
    }

    // 可选：启动时插入几条示例数据，便于按你截图里的 /list 页面展示
    @Bean
    CommandLineRunner init(StudentRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new Student(null, "Mary", "mary@gmail.com"));
                repo.save(new Student(null, "Alex", "alex@gmail.com"));
                repo.save(new Student(null, "Dean", "dean@yahoo.com"));
            }
        };
    }
}