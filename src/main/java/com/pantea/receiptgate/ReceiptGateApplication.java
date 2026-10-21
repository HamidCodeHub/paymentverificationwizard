package com.pantea.receiptgate;

import com.pantea.receiptgate.config.GateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GateProperties.class)
public class ReceiptGateApplication {

    public static void main(String[] args) {
        // ImageIO / Graphics2D must not try to open a display on a headless server.
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(ReceiptGateApplication.class, args);
    }
}
