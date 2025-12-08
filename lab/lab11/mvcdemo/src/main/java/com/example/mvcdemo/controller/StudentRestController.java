package com.example.mvcdemo.controller;

import com.example.mvcdemo.model.Student;
import com.example.mvcdemo.service.StudentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/students")
public class StudentRestController {

    private final StudentService studentService;

    public StudentRestController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public List<Student> getStudents(@RequestParam(required = false) String email,
                                     @RequestParam(required = false) Integer age) {
        if (email != null && age != null) {
            return studentService.findByEmailContainsAndAgeLessThanEqual(email, age);
        } else if (email != null) {
            return studentService.findByEmailContains(email);
        } else if (age != null) {
            return studentService.findByAgeLessThanEqual(age);
        } else {
            return studentService.findAll();
        }
    }
}