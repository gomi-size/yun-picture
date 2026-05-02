package com.yupi.yupicturebackend.manager.websocket;

import com.yupi.yupicturebackend.manager.auth.SpaceUserAuthManager;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WbSocket拦截器
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceService spaceService;
    @Autowired
    private SpaceUserAuthManager spaceUserAuthManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        //获取到当前用户
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            //从请求中获取请求参数
            String pictureId = servletRequest.getParameter("pictureId");
            if (pictureId == null) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            //获取当前登录用户
            User loginUser = userService.getLoginUser(servletRequest);
            if (loginUser == null) {
                log.error("用户未登录,拒绝握手");
                return false;
            }

            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                log.error("图片不存在,拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (space == null) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                //如果是团队空间，并且有编辑者权限，才能链接
                if (!space.getSpaceType().equals(SpaceTypeEnum.TEAM.getValue())) {
                    log.error("不是团队空间拒绝握手");
                }

            }
            //校验当前用户的权限，是否满足
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("没有权限，拒绝握手");
                return false;
            }
            //设置用户登录信息等属性到WebSocket会话中
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.parseLong(pictureId));//记得转化为lang类型

        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
