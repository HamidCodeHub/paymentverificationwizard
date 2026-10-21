package com.pantea.receiptgate.config;

import com.pantea.receiptgate.web.ApiKeyFilter;
import com.pantea.receiptgate.web.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GateProperties props;

    public WebConfig(GateProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = props.getAllowedOrigins();
        if (origins == null || origins.isBlank()) {
            return; // default: browser calls are blocked, WordPress proxies server-side
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Api-Key")
                .maxAge(3600);
    }

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> bean = new FilterRegistrationBean<>(new ApiKeyFilter(props));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(new RateLimitFilter(props));
        bean.addUrlPatterns("/api/v1/receipts/check");
        bean.setOrder(2);
        return bean;
    }
}
