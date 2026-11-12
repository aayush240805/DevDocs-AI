package com.devdocs;

import com.devdocs.config.TestEmbeddingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestEmbeddingConfig.class)
class DevDocsApplicationTests {

	@Test
	void contextLoads() {
	}
}
