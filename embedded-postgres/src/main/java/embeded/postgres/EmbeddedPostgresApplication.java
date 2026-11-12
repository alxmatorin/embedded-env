package embeded.postgres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmbeddedPostgresApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmbeddedPostgresApplication.class, args);
    }
}
