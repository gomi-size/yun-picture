package com.yupi.yupicturebackend.manager.dsiruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑时间生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {
    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        //获取到缓存区
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        //获取倒可以生成的位置
        long sequence = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(sequence);
        pictureEditEvent.setPictureId(pictureId);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        //发布事件
        ringBuffer.publish(sequence);
    }
    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }

}
