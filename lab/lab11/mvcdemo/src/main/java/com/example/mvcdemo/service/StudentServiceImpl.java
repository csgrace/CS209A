package com.example.mvcdemo.service;

import com.example.mvcdemo.model.Student;
import com.example.mvcdemo.repository.StudentRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;

    public StudentServiceImpl(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    @Override
    public List<Student> findByEmailContains(String email) {
        return studentRepository.findByEmailContainingIgnoreCase(email);
    }

    @Override
    public List<Student> findByAgeLessThanEqual(Integer age) {
        return studentRepository.findByAgeLessThanEqual(age);
    }

    @Override
    public List<Student> findByEmailContainsAndAgeLessThanEqual(String email, Integer age) {
        return studentRepository.findByEmailContainingIgnoreCaseAndAgeLessThanEqual(email, age);
    }

    // ---------- 新增的方法实现，供 MVC 和应用初始化使用 ----------
    @Override
    public void addStudents() {
        // 如果你不想重复插入，可以先检查是否已有数据；这里简单示范插入几条示例数据
        List<Student> existing = studentRepository.findAll();
        if (!existing.isEmpty()) {
            return; // 已有数据时不重复插入
        }
        List<Student> init = new ArrayList<>();
        init.add(new Student("Alice", "alice@example.com", 20));
        init.add(new Student("Bob", "bob@school.edu", 25));
        init.add(new Student("Carol", "carol@example.com", 22));
        studentRepository.saveAll(init);
    }

    @Override
    public List<Student> getStudents() {
        return studentRepository.findAll();
    }

    @Override
    public List<Student> findByEmailLike(String email) {
        if (email == null) {
            return studentRepository.findAll();
        }
        return studentRepository.findByEmailContainingIgnoreCase(email);
    }
}