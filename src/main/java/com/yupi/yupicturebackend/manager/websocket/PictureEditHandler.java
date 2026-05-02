package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.UserVO;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑WebSocket
 */
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    // 每张图片的编辑状态（用来控制编辑图片的），key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话（用来将操作分发给set中的用户），key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();


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
        Long pictureId = (Long) session.getAttributes().get("PictureId");
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
        broadcastToPicture(pictureId, responseMessage);

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
        //get PictureEditActionEnum
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);

        //从session中获取公共数据
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("PictureId");

        //对
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                handleEnterActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                handleEnterExitMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:
                // error message set to session
                PictureEditResponseMessage responseMessage = new PictureEditResponseMessage();
                responseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                responseMessage.setMessage("message is error");
                responseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(responseMessage)));
        }

    }

    /**
     * 用户进入图片编辑页面
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    private void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {

        if (!pictureEditingUsers.containsKey(pictureId)) {
            pictureEditingUsers.put(pictureId, user.getId());

            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            //全广播
            broadcastToPicture(pictureId, pictureEditResponseMessage);

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
    private void handleEnterActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        //获取到当前操作人的id
        Long editPictureUserId = pictureEditingUsers.get(pictureId);


        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum enumByValue = PictureEditActionEnum.getEnumByValue(editAction);
        if (enumByValue == null) {
            return;
        }
        //确认当前编辑者
        if(editPictureUserId!=null&&editPictureUserId.equals(user.getId())) {
            //是当前编辑者进行操作
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), editAction);
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            pictureEditResponseMessage.setEditAction(editAction);
            ///要除掉自己
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);

        }


    }

    /**
     * 用户退出编辑操作
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    private void handleEnterExitMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {

        Long editPictureUserId = pictureEditingUsers.get(pictureId);
        //确认当前角色才能退出
        if (editPictureUserId != null&&editPictureUserId.equals(user.getId())) {
            pictureEditingUsers.remove(pictureId);
            //构造消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(BeanUtil.copyProperties(user, UserVO.class));
            ///全广播
            broadcastToPicture(pictureId, pictureEditResponseMessage);
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
        Long pictureId = (Long) attributes.get("PictureId");
        handleEnterExitMessage(null, session,user ,pictureId );
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
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 广播给该用户的所有用户（支持排除某个Session）
     */
    public void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage, WebSocketSession
            session) throws Exception {

        Set<WebSocketSession> Sessions = pictureSessions.get(pictureId);
        //创建 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        //配置序列化:将Long类型转为String，解决丢失精度问题
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);// 支持 long 基本类型
        objectMapper.registerModule(module);

        //向每一个session发送当前操作
        if (CollUtil.isNotEmpty(Sessions)) {
            String message = objectMapper.writeValueAsString(responseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession webSocketSession : Sessions) {

                //排除掉操作人
                if (session != null && session.equals(webSocketSession)) {
                    continue;
                }
                if (webSocketSession.isOpen()) {
                    webSocketSession.sendMessage(textMessage);
                }

            }
        }

    }

    /**
     * 广播给该用户的所有用户
     */
    public void broadcastToPicture(Long pictureId, PictureEditResponseMessage responseMessage) throws Exception {

        Set<WebSocketSession> Sessions = pictureSessions.get(pictureId);
        //创建 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        //配置序列化:将Long类型转为String，解决丢失精度问题
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);// 支持 long 基本类型
        objectMapper.registerModule(module);

        //向每一个session发送当前操作
        if (CollUtil.isNotEmpty(Sessions)) {
            String message = objectMapper.writeValueAsString(responseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession webSocketSession : Sessions) {
                if (webSocketSession.isOpen()) {
                    webSocketSession.sendMessage(textMessage);
                }

            }
        }

    }
}
