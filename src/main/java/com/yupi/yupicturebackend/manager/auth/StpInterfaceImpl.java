package com.yupi.yupicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    /**
     * 返回一个账号所拥有的权限码集合 
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 从请求中获取上下文
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        //动态获取 Request 对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        //CONTENT_TYPE获取json
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest ;
        //获取请求参数这是一个 HTTP 协议的标准请求头键名。它告诉服务器，这个请求带过来的数据是什么格式的。
        if(ContentType.JSON.getValue().equals(contentType)){
            String body = ServletUtil.getBody(request);
            authRequest= JSONUtil.toBean(body, SpaceUserAuthContext.class);
        }else {
            Map<String ,String> paramMap=ServletUtil.getParamMap(request);
            authRequest=BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        //根据请求路径区分id的含义
        //这里的id就是这个类的id
        Long id = authRequest.getId();
        if(ObjectUtil.isNotNull(id)){
            //获取到请求路径的业务前提
            String requestURI = request.getRequestURI();
            //先替换掉上下文前缀
            String partUrl = requestURI.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUrl, "/", false);
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }
}
