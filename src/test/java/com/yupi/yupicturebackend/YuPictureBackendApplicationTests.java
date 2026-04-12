package com.yupi.yupicturebackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class YuPictureBackendApplicationTests {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisConnect() {
        System.out.println("====== 开始测试 Redis 连接 ======");

        // 1. 获取操作 String 类型数据的对象
        ValueOperations<String, String> valueOperations = stringRedisTemplate.opsForValue();

        // 2. 测试写入数据 (Key: test:hello, Value: world)
        valueOperations.set("test:hello", "world");
        System.out.println("✅ 成功写入数据到 Redis！");

        // 3. 测试读取刚才写入的数据
        String result = valueOperations.get("test:hello");
        System.out.println("✅ 从 Redis 读取到的值为: " + result);

        // 4. 断言验证（可选）
        // org.junit.jupiter.api.Assertions.assertEquals("world", result);

        System.out.println("====== Redis 连接测试完美通过！======");
    }
}
