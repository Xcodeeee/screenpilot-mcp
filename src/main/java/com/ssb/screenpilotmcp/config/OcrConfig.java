package com.ssb.screenpilotmcp.config;

import io.github.lxw112190.ppocr.ppocr.PaddleOcr;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Configuration
public class OcrConfig {

    // 构建Bean
    @Bean(destroyMethod = "close")
    public PaddleOcr paddleOcr() throws IOException {
        Path dir = Files.createTempDirectory("ppocr-models");
        dir.toFile().deleteOnExit();

        Path det  = release("models/ppocrv6-tiny/det.lwm",          dir, "det.lwm");
        Path cls  = release("models/ppocrv6-tiny/cls.lwm",          dir, "cls.lwm");
        Path rec  = release("models/ppocrv6-tiny/rec.lwm",          dir, "rec.lwm");
        Path dict = release("models/ppocrv6-tiny/ppocr_keys.txt",   dir, "ppocr_keys.txt");

        // 4 参数版本 = Scalar 路径，Java 21 可用
        return PaddleOcr.load(det, cls, rec, dict);
    }

    private Path release(String resource, Path dir, String name) throws IOException {
        Path target = dir.resolve(name);
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}