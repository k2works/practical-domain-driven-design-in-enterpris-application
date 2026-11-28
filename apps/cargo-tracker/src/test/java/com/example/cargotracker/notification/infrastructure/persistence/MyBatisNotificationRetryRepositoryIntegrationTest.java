package com.example.cargotracker.notification.infrastructure.persistence;

import com.example.cargotracker.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 再送の列と引き取りを PostgreSQL で確かめる。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MyBatisNotificationRetryRepositoryIntegrationTest extends NotificationRetryRepositoryContract {}
