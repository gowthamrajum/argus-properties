package com.argus.properties.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI argusPropertiesOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Argus BPMN Shape Properties API")
            .version("v1")
            .description("""
                Reference catalogue of every shape a Camunda 7 / Fluxnova BPMN 2.0 file can \
                contain, and every property each one carries - BPMN attributes, camunda: \
                extension attributes and elements, and the diagram-interchange properties that \
                only affect rendering. Static reference data: this service never parses a .bpmn \
                file, it describes what one may contain.""")
            .license(new License().name("Apache-2.0")));
  }
}
