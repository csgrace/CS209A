package cs209a.finalproject_demo;
// http://localhost:8081/
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
public class FinalProjectDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinalProjectDemoApplication.class, args);
    }

}
