package com.yupi.yupicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yupi.yupicturebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    //获取路径
    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;
    @Resource
    private PictureService pictureService;
    @Resource
    private SpaceUserService spaceUserService ;

    /**
     * 返回一个账号所拥有的权限码集合 
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {

            // 判断 loginType，仅对类型为 "space" 进行权限校验
            if (!StpKit.SPACE_TYPE.equals(loginType)) {
                return new ArrayList<>();
            }
            // 管理员权限，表示权限校验通过
            List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
            // 获取上下文对象
            SpaceUserAuthContext authContext = getAuthContextByRequest();
            // 如果所有字段都为空，表示查询公共图库，可以通过
            if (isAllFieldsNull(authContext)) {
                return ADMIN_PERMISSIONS;
            }
            // 获取 userId
            User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
            if (loginUser == null) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
            }
            Long userId = loginUser.getId();


            //以下是根据SpaceUser对象或者SpaceUserId获取到对应的权限
            // 优先从上下文中获取 SpaceUser 对象
            SpaceUser spaceUser = authContext.getSpaceUser();
            if (spaceUser != null) {
                //有的话直接根据对应身份拿到权限
                return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
            }
            // 如果有 spaceUserId，必然是团队空间，通过数据库查询 SpaceUser 对象
            Long spaceUserId = authContext.getSpaceUserId();
            if (spaceUserId != null) {
                spaceUser = spaceUserService.getById(spaceUserId);
                if (spaceUser == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
                }
                // 取出当前登录用户对应的 spaceUser，查出去后直接返回，在下面进行对应权限的获取
                SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                        .eq(SpaceUser::getUserId, userId)
                        .one();
                if (loginSpaceUser == null) {
                    return new ArrayList<>();
                }
                // 这里会导致管理员在私有空间没有权限，可以再查一次库处理。
                return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
            }


            //以下是根据SpaceId或者pictureId获取到对应的权限

            // 如果没有 spaceUserId，尝试通过 spaceId 或 pictureId 获取 Space 对象并处理
            Long spaceId = authContext.getSpaceId();
            if (spaceId == null) {
                // 如果没有 spaceId，通过 pictureId 获取 Picture 对象和 Space 对象
                Long pictureId = authContext.getPictureId();
                // 图片 id 也没有，则默认通过权限校验
                if (pictureId == null) {
                    return ADMIN_PERMISSIONS;
                }
                Picture picture = pictureService.lambdaQuery()
                        .eq(Picture::getId, pictureId)
                        .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                        .one();
                if (picture == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
                }
                spaceId = picture.getSpaceId();
                // 公共图库，仅本人或管理员可操作
                if (spaceId == null) {
                    if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                        return ADMIN_PERMISSIONS;
                    } else {
                        // 不是自己的图片，仅可查看
                        return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                    }
                }
            }

            //这边是获取到Space对象进行权限的校验

            // 获取 Space 对象
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
            }
            // 根据 Space 类型判断权限
            if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                // 私有空间，仅本人或管理员有权限
                if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            } else {
                // 团队空间，查询 SpaceUser 并获取角色和权限
                spaceUser = spaceUserService.lambdaQuery()
                        .eq(SpaceUser::getSpaceId, spaceId)
                        .eq(SpaceUser::getUserId, userId)
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                }
                return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
            }

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

    /**
     * 判断对象所有字段是否为空
     * @param object
     * @return
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空（通过反射拿到）
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }

}
