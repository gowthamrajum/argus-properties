package com.argus.properties.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular documentation site from the same jar as the API.
 *
 * <p>A single-page app owns its own routing, so the server has to answer a deep link like
 * {@code /shapes/user-task} with {@code index.html} and let the router sort it out. Without this,
 * that URL is a 404 - the app works when you click through to it and breaks when you reload or
 * share the link, which is the worst way for it to break.
 *
 * <p>The fallback is deliberately narrow. Anything under {@code /api} or {@code /actuator} must
 * keep returning a real 404, because an unknown shape id answering with an HTML page instead of
 * the service's JSON error would be a far more confusing failure than a missing route.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
    registry.addResourceHandler("/**")
        .addResourceLocations("classpath:/static/")
        .resourceChain(true)
        .addResolver(new PathResourceResolver() {
          @Override
          protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location)
              throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
              return requested;
            }
            if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
              return null;
            }
            // A client-side route: hand back the shell and let the Angular router resolve it.
            return location.createRelative("index.html");
          }
        });
  }
}
