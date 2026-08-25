package com.kant.cardhub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.task.scheduling.enabled=false",
                "cardhub.worker.enabled=false"
        }
)
@ActiveProfiles("test")
class WorkerApplicationContextTest {

    @Test
    void startsWithoutServletSecurityInfrastructure() {
    }
}
