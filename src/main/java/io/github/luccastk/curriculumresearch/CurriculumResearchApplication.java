package io.github.luccastk.curriculumresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CurriculumResearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(CurriculumResearchApplication.class, args);
	}

}
