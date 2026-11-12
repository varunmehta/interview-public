package com.weavelab.interview.seed;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.startup-check", havingValue = "true", matchIfMissing = true)
@Order(1)
public class StartupCheck implements CommandLineRunner {

    private final DataSeeder seeder;

    public StartupCheck(DataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(String... args) {
        if (seeder.isEmpty()) {
            System.out.println("Database is empty. Run with --seed first:");
            System.out.println("  ./mvnw spring-boot:run -Dspring-boot.run.arguments=\"--app.seed=true --app.startup-check=false\"");
            System.exit(1);
        }
    }
}
