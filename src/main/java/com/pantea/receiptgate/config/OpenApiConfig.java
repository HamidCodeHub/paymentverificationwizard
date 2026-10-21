package com.pantea.receiptgate.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private final GateProperties props;

    @Value("${server.port:8086}")
    private int port;

    public OpenApiConfig(GateProperties props) {
        this.props = props;
    }

    @Bean
    public OpenAPI receiptGateOpenApi() {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("Pantea Receipt Gate API")
                        .version("1.0.0")
                        .description("""
                                Decides whether an uploaded image is a bank-transfer receipt, so the shop can forward
                                it to the admin, ask the customer for a clearer one, or block it.

                                **Two free signals.** Tesseract reads the Persian *words* on the image (never the
                                numbers) and scores receipt vocabulary such as «پیگیری» and «شبا مقصد». A CLIP probe,
                                trained on the images in `references/`, judges whether the image *looks* like a
                                receipt even when nothing is readable. Words decide; CLIP only turns an unreadable
                                receipt into UNSURE instead of a block.

                                **Not payment verification.** A receipt can be pending, cancelled or forged. RECEIPT
                                means "worth a human look", and the admin confirms the money in the bank account.
                                """)
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Local development"),
                        new Server().url("https://receipts.example.com").description("Production - replace with your host")
                ));

        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            api.components(new Components().addSecuritySchemes("ApiKey",
                            new SecurityScheme()
                                    .type(SecurityScheme.Type.APIKEY)
                                    .in(SecurityScheme.In.HEADER)
                                    .name("X-Api-Key")
                                    .description("Shared secret matching gate.api-key. Not required for GET /health.")))
                    .addSecurityItem(new SecurityRequirement().addList("ApiKey"));
        }
        return api;
    }
}
