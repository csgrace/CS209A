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

    @Override
    public void addStudents() {
        if (!studentRepository.findAll().isEmpty()) return;
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
        if (email == null) return studentRepository.findAll();
        return studentRepository.findByEmailContainingIgnoreCase(email);
    }
}