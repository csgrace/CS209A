package com.example.mvcdemo.repository;

import com.example.mvcdemo.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    // 包含 email（忽略大小写）
    List<Student> findByEmailContainingIgnoreCase(String email);

    // age <= 给定值
    List<Student> findByAgeLessThanEqual(Integer age);

    // email 包含 且 age <=
    List<Student> findByEmailContainingIgnoreCaseAndAgeLessThanEqual(String email, Integer age);
}
