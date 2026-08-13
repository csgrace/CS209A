package cs209a.finalproject_demo;

import importdata.InsertDatabase;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {

    @Bean
    CommandLineRunner importDatasetIfDatabaseIsEmpty(JdbcTemplate jdbcTemplate) {
        return args -> {
            InsertDatabase importer = new InsertDatabase();
            Integer questionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM questions", Integer.class);
            if (questionCount == null || questionCount == 0) {
                importer.importAllData();
            }
        };
    }
}
