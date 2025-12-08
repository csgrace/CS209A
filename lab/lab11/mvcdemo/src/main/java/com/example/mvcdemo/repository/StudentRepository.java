package com.example.mvcdemo.repository;

import com.example.mvcdemo.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByEmailContainingIgnoreCase(String email);
    List<Student> findByAgeLessThanEqual(Integer age);
    List<Student> findByEmailContainingIgnoreCaseAndAgeLessThanEqual(String email, Integer age);
}