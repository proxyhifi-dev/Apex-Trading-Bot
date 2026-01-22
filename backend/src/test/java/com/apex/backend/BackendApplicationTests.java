package com.apex.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"jwt.secret=01234567890123456789012345678901"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
