package pt.lisboapedala.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração web da api: todos os controllers de {@code api.rest} ficam sob a base {@code /api/v1}
 * (ARCHITECTURE.md §7.3). O Actuator ({@code /actuator/health}, endpoint 20) não é afetado.
 */
@Configuration
public class ApiWebConfig implements WebMvcConfigurer {

    public static final String BASE_PATH = "/api/v1";
    private static final String REST_PACKAGE = "pt.lisboapedala.api.rest";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(BASE_PATH, HandlerTypePredicate.forBasePackage(REST_PACKAGE));
    }
}
