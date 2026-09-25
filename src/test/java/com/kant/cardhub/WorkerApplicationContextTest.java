package com.kant.cardhub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.task.scheduling.enabled=false",
                "cardhub.worker.enabled=false"
        }
)
@ActiveProfiles("test")
class WorkerApplicationContextTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void startsWithoutServletSecurityInfrastructure() {
        assertThat(applicationContext).isNotNull();
    }
}
