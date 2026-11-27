package com.example.cargotracker.notification.infrastructure.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 再送の列と引き取りを H2（dev プロファイル）で確かめる。 */
@SpringBootTest
@ActiveProfiles("dev")
class H2NotificationRetryRepositoryTest extends NotificationRetryRepositoryContract {}
