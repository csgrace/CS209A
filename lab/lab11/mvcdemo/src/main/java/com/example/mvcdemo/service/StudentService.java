package com.example.mvcdemo.service;

import com.example.mvcdemo.model.Student;
import java.util.List;

public interface StudentService {
    List<Student> findAll();
    List<Student> findByEmailContains(String email);
    List<Student> findByAgeLessThanEqual(Integer age);
    List<Student> findByEmailContainsAndAgeLessThanEqual(String email, Integer age);

    // 为 MVC Controller / 应用初始化需要的方法（之前缺失）
    void addStudents();
    List<Student> getStudents();
    List<Student> findByEmailLike(String email);
}