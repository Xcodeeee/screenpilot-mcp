package com.ssb.screenpilotmcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class ScreenpilotMcpApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ScreenpilotMcpApplication.class)
                .headless(false)
                .run(args);
    }
}