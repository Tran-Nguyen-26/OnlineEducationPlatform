package com.oep.worker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "oep.ffmpeg")
@Data
public class FfmpegConfig {

    private String binary = "/usr/bin/ffmpeg";
    private String ffprobe = "/usr/bin/ffprobe";
    private String tempDir = "/tmp/oep";
}
