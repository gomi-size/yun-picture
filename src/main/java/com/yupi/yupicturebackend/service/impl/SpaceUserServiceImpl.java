package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.Utils.QueryWrapperUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserEditRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.mapper.SpaceUserMapper;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * @author MECHREVO
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service实现
 * @createDate 2026-04-24 15:54:39
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {

    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        // 创建时，空间 id 和用户 id 必填
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        if (add) {
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        if (spaceRole != null && spaceRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间角色不存在");
        }
    }


    /**
     * 获取多例空间用户
     *
     * @param spaceUserQueryRequest
     * @return
     */
    @Override
    public List<SpaceUserVO> getSpaceVOList(SpaceUserQueryRequest spaceUserQueryRequest) {
        //校验参数
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR, "请求参数错误");

        List<SpaceUser> spaceUserList = list(QueryWrapperUtils.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(ObjectUtil.isEmpty(spaceUserList), ErrorCode.NOT_FOUND_ERROR);

        //参数转换
        List<SpaceUserVO> spaceUserVOList = spaceUserList.stream().map(SpaceUserVO::objToVo).collect(Collectors.toList());

        //提取userId和spaceId,用set承接
        Set<Long> UserIdList = spaceUserVOList.stream().map(SpaceUserVO::getUserId).collect(Collectors.toSet());
        Set<Long> spaceIdList = spaceUserVOList.stream().map(SpaceUserVO::getSpaceId).collect(Collectors.toSet());

        //进行数据库查询后使用map进行存储，每一个key就是用户的id，每一个value就是查处的用户
        Map<Long, List<User>> UserIdListMap = userService.listByIds(UserIdList).stream().collect(Collectors.groupingBy(User::getId));
        Map<Long, List<Space>> SpaceIdListMap = spaceService.listByIds(spaceIdList).stream().collect(Collectors.groupingBy(Space::getId));

        //进行填充spaceUserVOList中的值
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();

            //填充用户信息
            User user = new User();
            if (UserIdListMap.containsKey(userId)) {
                //只有一条数据
                user = UserIdListMap.get(userId).get(0);
            }
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            spaceUserVO.setUser(userVO);

            //填充空间信息
            Space space = new Space();
            if (SpaceIdListMap.containsKey(spaceId)) {
                space = SpaceIdListMap.get(spaceId).get(0);
            }
            SpaceVO spaceVO = BeanUtil.copyProperties(space, SpaceVO.class);
            spaceUserVO.setSpace(spaceVO);
        });
        return spaceUserVOList;
    }


    /**
     * 添加空间人员
     *
     * @param spaceUserAddRequest
     * @return
     */
    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 数据库操作
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);


        return spaceUser.getId();
    }

    /**
     * 编辑空间成员信息（主要是编辑这个成员在这个空间角色）
     *
     * @param spaceUserEditRequest
     */
    @Override
    public void editSpaceUser(SpaceUserEditRequest spaceUserEditRequest) {
        //校验参数
        SpaceUser spaceUser = BeanUtil.copyProperties(spaceUserEditRequest, SpaceUser.class);
        validSpaceUser(spaceUser, false);

        //取出老参数进行校验
        SpaceUser oldSpaceUser = getById(spaceUser.getId());
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR, "没有该参数");

        //进行操作数据库
        boolean result = updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "操作数据库失败");

    }


    /**
     * 获取单例空间用户
     *
     * @param spaceUser
     * @return
     */
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联查询用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            spaceUserVO.setUser(userVO);
        }
        // 关联查询空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = BeanUtil.copyProperties(space, SpaceVO.class);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }
}




