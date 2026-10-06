package spike.totp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** TS-01 のスパイクのアプリケーション（学習テストのためだけに起動する）。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeApplication.class, args);
    }
}
