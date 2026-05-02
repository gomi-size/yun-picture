package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.dsiruptor.PictureEditEventProducer;
import com.yupi.yupicturebackend.manager.redisMessage.RedisWebSocketConfig;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 图片编辑WebSocket
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {
/*    // 每张图片的编辑状态（用来控制编辑图片的），key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();*/

    // 保存所有连接的会话（用来将操作分发给set中的用户），key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 分布式锁的前缀常量
    private static final String EDIT_LOCK_PREFIX = "picture:edit:lock:";

    /**
     * 链接建立成功（成员加入空间）
     *
     * @param session 加入成员的session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        //保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        //刚进入的set肯定是空的所以要设定一个空的set
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        //将这个session放入到mapper中
        pictureSessions.get(pictureId).add(session);

        //构造响应
        PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
        responseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户%s加入到该空间", user.getUserName());
        responseMessage.setMessage(message);

        //要脱敏
        responseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));

        //这个要广播给所有成员
        publishToRedis(responseMessage);

    }

    /**
     * 收到前端发送的消息，根据消息类别处理消息
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        //message->PictureEditResponseMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        //从session中获取公共数据
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");

        //根据消息类型处理消息（生产到Disruptor环形队列中）
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage,session,user,pictureId);
    }

    /**
     * 用户进入图片编辑页面
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {

        //TODO 获取到分布式的钥匙
        String lockKey = EDIT_LOCK_PREFIX + pictureId;
        // TODO 尝试获取锁，设置 30 秒过期时间作为兜底（防止宕机死锁）
        Boolean lockSuccess = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, String.valueOf(user.getId()), 30, TimeUnit.SECONDS);

        //TODO
        if (Boolean.TRUE.equals(lockSuccess)) {
            /*pictureEditingUsers.put(pictureId, user.getId());*/

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            //TODO 全广播
            publishToRedis(pictureEditResponseMessage);

        }else {
            // 可选：发送获取锁失败的错误信息给当前用户
            log.warn("用户 {} 尝试编辑图片 {} 失败，锁已被占用", user.getId(), pictureId);
        }
    }

    /**
     * 用户开始编辑
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        String lockKey = EDIT_LOCK_PREFIX + pictureId;
        // 去 Redis 查一下现在的锁是不是还是当前用户的
        String currentEditorId = stringRedisTemplate.opsForValue().get(lockKey);

        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);

        if (actionEnum == null) {
            return;
        }
        //确认当前编辑者
        if( String.valueOf(user.getId()).equals(currentEditorId)) {
            // 每操作一次，顺手给锁续期 60 秒，防止正常编辑时锁过期被别人抢走
            stringRedisTemplate.expire(lockKey, 60, TimeUnit.SECONDS);
            //是当前编辑者进行操作
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), editAction);
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            pictureEditResponseMessage.setEditAction(editAction);
            ///要除掉自己
            publishToRedis(pictureEditResponseMessage);

        }


    }

    /**
     * 用户退出编辑操作
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterExitMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {

        String lockKey = EDIT_LOCK_PREFIX + pictureId;
        String currentEditorId = stringRedisTemplate.opsForValue().get(lockKey);
        //确认当前角色才能退出
        if (String.valueOf(user.getId()).equals(currentEditorId)) {
            //释放锁
            stringRedisTemplate.delete(lockKey);
            //构造消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            ///全广播
            publishToRedis(pictureEditResponseMessage);
        }
    }

    /**
     * 关闭链接释放资源
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        //移除用户编辑请求
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        handleEnterExitMessage(null, session,user ,pictureId );

        // 强行尝试释放锁（防止用户直接关掉浏览器）
        handleEnterExitMessage(null, session, user, pictureId);

        //删除会话中的成员
        Set<WebSocketSession> webSocketSessions = pictureSessions.get(pictureId);
        if (webSocketSessions != null) {
            webSocketSessions.remove(session);
            //如果这个会话都为空那么直接删除这个会话，释放资源
            if (webSocketSessions.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        //通知所有用户，该用户已经下线了
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户%s下线", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
        ///全广播
        publishToRedis(pictureEditResponseMessage);
    }


    /**
     * 【核心改动】不直接发，而是把消息丢给 Redis 邮局
     */
    private void publishToRedis(PictureEditResponseMessage responseMessage) {
        String jsonMessage = JSONUtil.toJsonStr(responseMessage);
        stringRedisTemplate.convertAndSend(RedisWebSocketConfig.PICTURE_EDIT_CHANNEL, jsonMessage);
    }

    /**
     * 被 Redis 监听器调用，负责真正的本地下发
     */
    public void broadcastToLocalPicture(Long pictureId, PictureEditResponseMessage responseMessage) throws Exception {
        Set<WebSocketSession> sessions = pictureSessions.get(pictureId);
        if (CollUtil.isEmpty(sessions)) {
            return;
        }

        // 1. 获取这个消息是由哪个用户触发的（也就是“发送者”）
        Long senderId = responseMessage.getUser().getId();
        // 2. 获取当前消息的类型
        String messageType = responseMessage.getType();

        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);

        String messageStr = objectMapper.writeValueAsString(responseMessage);
        TextMessage textMessage = new TextMessage(messageStr);

        for (WebSocketSession webSocketSession : sessions) {
            if (webSocketSession.isOpen()) {
                // 3. 获取当前正准备发送的这个 WebSocket 连接属于哪个用户（“接收者”）
                Map<String, Object> attributes = webSocketSession.getAttributes();
                User sessionUser = (User) attributes.get("user");
                Long receiverId = sessionUser.getId();

                // 4. 【核心过滤逻辑】：如果是编辑动作，且发送者和接收者是同一个人，则跳过！
                if (PictureEditMessageTypeEnum.EDIT_ACTION.getValue().equals(messageType)) {
                    if (senderId.equals(receiverId)) {
                        continue; // 成功排除了自己
                    }
                }

                // 其他类型的消息，或者不是发给自己的编辑动作，正常下发
                webSocketSession.sendMessage(textMessage);
            }
        }
    }
}
//
///**
// * 广播给该用户的所有用户（支持排除某个Session）
// */
//public void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage, WebSocketSession
//        session) throws Exception {
//
//    Set<WebSocketSession> Sessions = pictureSessions.get(pictureId);
//    //创建 ObjectMapper
//    ObjectMapper objectMapper = new ObjectMapper();
//
//    //配置序列化:将Long类型转为String，解决丢失精度问题
//    SimpleModule module = new SimpleModule();
//    module.addSerializer(Long.class, ToStringSerializer.instance);
//    module.addSerializer(Long.TYPE, ToStringSerializer.instance);// 支持 long 基本类型
//    objectMapper.registerModule(module);
//
//    //向每一个session发送当前操作
//    if (CollUtil.isNotEmpty(Sessions)) {
//        String message = objectMapper.writeValueAsString(responseMessage);
//        TextMessage textMessage = new TextMessage(message);
//        for (WebSocketSession webSocketSession : Sessions) {
//
//            //排除掉操作人
//            if (session != null && session.equals(webSocketSession)) {
//                continue;
//            }
//            if (webSocketSession.isOpen()) {
//                webSocketSession.sendMessage(textMessage);
//            }
//
//        }
//    }
//
//}
//
///**
// * 广播给该用户的所有用户
// */
//public void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage) throws Exception {
//
//    Set<WebSocketSession> Sessions = pictureSessions.get(pictureId);
//    //创建 ObjectMapper
//    ObjectMapper objectMapper = new ObjectMapper();
//
//    //配置序列化:将Long类型转为String，解决丢失精度问题
//    SimpleModule module = new SimpleModule();
//    module.addSerializer(Long.class, ToStringSerializer.instance);
//    module.addSerializer(Long.TYPE, ToStringSerializer.instance);// 支持 long 基本类型
//    objectMapper.registerModule(module);
//
//    //向每一个session发送当前操作
//    if (CollUtil.isNotEmpty(Sessions)) {
//        String message = objectMapper.writeValueAsString(responseMessage);
//        TextMessage textMessage = new TextMessage(message);
//        for (WebSocketSession webSocketSession : Sessions) {
//            if (webSocketSession.isOpen()) {
//                webSocketSession.sendMessage(textMessage);
//            }
//
//        }
//    }
//
//}