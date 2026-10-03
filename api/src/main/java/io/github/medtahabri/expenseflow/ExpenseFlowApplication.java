package io.github.medtahabri.expenseflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ExpenseFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseFlowApplication.class, args);
    }
}
