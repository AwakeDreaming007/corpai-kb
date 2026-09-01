package com.xufg;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "langchain4j.zhipu.api-key=test-key",
        "langchain4j.deepseek.api-key=test-key",
        "langchain4j.dashscope.api-key=test-key"
})
class Langchain4jJavaApplicationTests {

    @Test
    void contextLoads() {
    }

}
