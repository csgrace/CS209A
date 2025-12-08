package com.example.mvcdemo.controller;

import com.example.mvcdemo.model.Student;
import com.example.mvcdemo.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(controllers = StudentRestController.class)
public class StudentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StudentService studentService;

    private Student s1;
    private Student s2;
    private Student s3;

    @BeforeEach
    void setup() {
        s1 = new Student(1L, "Alice", "alice@example.com", 20);
        s2 = new Student(2L, "Bob", "bob@school.edu", 25);
        s3 = new Student(3L, "Carol", "carol@example.com", 22);
    }

    @Test
    void getAllStudents_returnsAll() throws Exception {
        List<Student> all = Arrays.asList(s1, s2, s3);
        when(studentService.findAll()).thenReturn(all);

        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(all.size()))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    @Test
    void getStudentsByEmail_returnsMatching() throws Exception {
        // 查询包含 "example" 的 email -> s1, s3
        List<Student> matched = Arrays.asList(s1, s3);
        when(studentService.findByEmailContains("example")).thenReturn(matched);

        mockMvc.perform(get("/api/students").param("email", "example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(matched.size()))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    void getStudentsByAge_returnsLessEqual() throws Exception {
        // age <= 22 -> s1 (20), s3 (22)
        List<Student> matched = Arrays.asList(s1, s3);
        when(studentService.findByAgeLessThanEqual(22)).thenReturn(matched);

        mockMvc.perform(get("/api/students").param("age", "22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(matched.size()))
                .andExpect(jsonPath("$[1].age").value(22));
    }

    @Test
    void getStudentsByEmailAndAge_returnsMatching() throws Exception {
        // email contains "example" and age <= 21 -> s1 only
        List<Student> matched = Arrays.asList(s1);
        when(studentService.findByEmailContainsAndAgeLessThanEqual("example", 21)).thenReturn(matched);

        mockMvc.perform(get("/api/students").param("email", "example").param("age", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }
}