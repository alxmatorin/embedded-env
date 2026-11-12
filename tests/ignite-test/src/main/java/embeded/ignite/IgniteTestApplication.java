package embeded.ignite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * При запуске main из IntelliJ IDEA добавь в VM options параметры ignite.jvm.arguments
 * из корневого pom.xml, как для EmbeddedIgniteApplication.
 * Для spring-boot:run эти параметры применяются автоматически.
 */
@SpringBootApplication
public class IgniteTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IgniteTestApplication.class, args);
    }
}
