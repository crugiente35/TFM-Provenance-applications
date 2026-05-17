package io.carranza.jpeg_trust_orchestrator;

import org.mipams.jpegtrust.config.JpegTrustConfig;
import org.mipams.jumbf.config.JumbfConfig;
import org.mipams.privsec.config.PrivsecConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
    JpegTrustConfig.class,
    JumbfConfig.class,
    PrivsecConfig.class
})
public class JpegTrustOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(JpegTrustOrchestratorApplication.class, args);
    }
}