package com.example.cargotracker.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** 定期処理のスレッドが 1 本だと、遅い送信の再送が滞留の監視や取込の確認を遅らせる（W10-1 の締めのレビュー）。 */
class SchedulingPoolConfigurationTest {

    @Test
    void 定期処理のスレッドは定期処理の数以上にする() throws IOException {
        Properties properties = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

        assertThat(Integer.parseInt(properties.getProperty("spring.task.scheduling.pool.size", "1")))
                .isGreaterThanOrEqualTo(4);
    }
}
