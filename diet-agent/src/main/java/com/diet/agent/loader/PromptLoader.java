package com.diet.agent.loader;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * diet 专用 Prompt 加载器。
 * 使用 classpath 读取资源，保证本地运行和打包成 JAR 后都能正常加载。
 */
@Component
public class PromptLoader {
    /**
     * 从 classpath 读取 Prompt 文本。
     * 如果资源不存在，调用方可以选择 catch 异常并使用内联兜底 Prompt。
     */
    public String load(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("加载 Prompt 失败: " + path, e);
        }
    }
}