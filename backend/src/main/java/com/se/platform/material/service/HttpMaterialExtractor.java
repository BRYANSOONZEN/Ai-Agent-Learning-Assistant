package com.se.platform.material.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 调本机常驻的 Python 资料处理服务（tools/material_kp/service.py）。
 * PDF：本机取文字 + DeepSeek 看图；视频：本机语音识别。一份资料可能要几分钟，所以超时给得很长，只在后台线程里调用。
 */
@Component
public class HttpMaterialExtractor implements MaterialExtractor {

    private final String url;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public HttpMaterialExtractor(@Value("${platform.material.service-url:http://127.0.0.1:8091}") String url,
                                 ObjectMapper json) {
        this.url = url;
        this.json = json;
    }

    @Override
    public Result extract(Path file, String kind) {
        try {
            String body = json.writeValueAsString(Map.of("path", file.toAbsolutePath().toString(), "kind", kind,
                    "lang", "zh", "images", true));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/extract"))
                    .timeout(Duration.ofMinutes(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode root = json.readTree(res.body());
            if (res.statusCode() != 200) {
                throw new ExtractException(root.path("error").asText("资料处理服务返回 " + res.statusCode()));
            }
            List<Segment> segs = new ArrayList<>();
            for (JsonNode s : root.path("segments")) {
                segs.add(new Segment(s.path("key").asText(), s.path("kind").asText(),
                        s.hasNonNull("page") ? s.get("page").asInt() : null,
                        s.hasNonNull("start") ? s.get("start").decimalValue() : null,
                        s.hasNonNull("end") ? s.get("end").decimalValue() : null,
                        s.path("text").asText()));
            }
            return new Result(segs, root.path("meta").toString());
        } catch (java.net.ConnectException e) {
            throw new ExtractException("资料处理服务没有启动（tools/material_kp/service.py）");
        } catch (IOException e) {
            throw new ExtractException("连不上资料处理服务：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractException("处理被中断");
        }
    }
}
