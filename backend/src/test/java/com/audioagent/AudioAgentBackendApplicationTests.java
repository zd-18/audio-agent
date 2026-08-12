package com.audioagent;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"audio.analysis.dispatch-mode=local",
		"audio.processing.enabled=false"
})
class AudioAgentBackendApplicationTests {

	@MockitoBean
	private MinioClient minioClient;

	@Test
	void contextLoads() {
	}

}
