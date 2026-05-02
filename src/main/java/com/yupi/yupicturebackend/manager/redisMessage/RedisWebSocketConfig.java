package com.yupi.yupicturebackend.manager.redisMessage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisWebSocketConfig {

    // 定义广播频道名称
    public static final String PICTURE_EDIT_CHANNEL = "picture_edit_events";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // 订阅图片编辑频道
        container.addMessageListener(listenerAdapter, new ChannelTopic(PICTURE_EDIT_CHANNEL));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(PictureEditRedisMessageListener receiver) {
        // 当收到消息时，调用 receiver 的 onMessage 方法
        return new MessageListenerAdapter(receiver, "onMessage");
    }
}