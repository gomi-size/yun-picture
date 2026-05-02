package com.yupi.yupicturebackend.manager.redisMessage;

import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.websocket.PictureEditHandler;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 接收来自 Redis 的跨节点 WebSocket 广播消息
 */
@Component
@Slf4j
public class PictureEditRedisMessageListener implements MessageListener {

    @Resource
    @Lazy // 延迟加载，解决与 PictureEditHandler 的循环依赖问题
    private PictureEditHandler pictureEditHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 获取消息体
            String messageBody = new String(message.getBody());
            log.info("收到 Redis 跨节点消息: {}", messageBody);
            
            // 转化为前面定义好地响应对象
            PictureEditResponseMessage responseMessage = JSONUtil.toBean(messageBody, PictureEditResponseMessage.class);
            Long pictureId = responseMessage.getPictureId();

            // 拿到消息后，调用本地的 Handler 向本机连接的用户广播
            if (pictureId != null) {
                pictureEditHandler.broadcastToLocalPicture(pictureId, responseMessage);
            }
            
        } catch (Exception e) {
            log.error("处理 Redis WebSocket 广播消息失败", e);
        }
    }
}