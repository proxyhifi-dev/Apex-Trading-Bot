package com.apex.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.boot.web.context.WebServerInitializedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private final Environment environment;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String address = environment.getProperty("server.address");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String host = (address == null || address.isBlank() || "0.0.0.0".equals(address)) ? "localhost" : address;
        String baseUrl = String.format("http://%s:%d%s", host, port, contextPath);
        log.info("Server started on port {} (base URL: {})", port, baseUrl);
    }
}
