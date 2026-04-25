package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.Utils.QueryWrapperUtils;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author MECHREVO
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2026-04-13 15:40:54
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {
    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SpaceUserService spaceUserService;


    /**
     * 获取分页（脱敏）
     * @param spaceQueryRequest
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        //整个的一个流程: 获取分页初始数据->将数据脱敏->根据数据获取用户ID->查询id后转为Map集合->后将所有的用户转化为VO并存入到SpaceVOList
        //->将数据放入SpaceVOPage返回即可
        //0.获取SpacePage
        int current = spaceQueryRequest.getCurrent();
        int pageSize = spaceQueryRequest.getPageSize();
        Page<Space> spacePage = page(new Page<>(current, pageSize), QueryWrapperUtils.getQueryWrapper(spaceQueryRequest));

        //1.获取分页的初始数据
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        //这里修改
        if (CollectionUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        //2.获取到图片脱敏后的数据
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());

        //3.获取到用户id列表
        Set<Long> uerIdset = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());

        //4.进行数据库查询，后转换为map集合，使用用户id当key
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(uerIdset).stream().collect(Collectors.groupingBy(User::getId));

        //5.将每一个UserVO都填充到SpaceVOList这个中
        spaceVOList.forEach(SpaceVO -> {
            User user = null;
            Long userId = SpaceVO.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            SpaceVO.setUser(BeanUtil.copyProperties(user, UserVO.class));
        });
        //6.将SpaceVOList放入到SpaceVOPage中就结束了
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 自动填充等级
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if(space.getMaxSize()==null){
                space.setMaxSize(maxSize);
            }
            if(space.getMaxCount()==null){
                long maxCount = spaceLevelEnum.getMaxCount();
            }
        }
    }

    /**
     * 编辑空间
     * @param spaceUpdateRequest
     * @param request
     * @return
     */
    @Override
    public Boolean updateSpace(SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {
        //流程:将DTO转化为实体类->参数校验->操作数据库

        //参数转换(将DTO转化为实体类)
        Space space = BeanUtil.copyProperties(spaceUpdateRequest, Space.class);
        //自动填充数据
        fillSpaceBySpaceLevel(space);
        //参数校验
        validSpace(space,false);

        //查数据库中是否有该空间
        Space oldSpace = getById(space.getId());
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        //操作数据库
        boolean result = updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;
    }

    /**
     * 修改空间（用户使用）
     * @param spaceEditRequest
     * @param request
     * @return
     */
    @Override
    public Boolean editSpace(SpaceEditRequest spaceEditRequest, HttpServletRequest request) {
        //流程:将DTO转化为实体类->参数校验->操作数据库
        //参数转换(将DTO转化为实体类)
        Space space = BeanUtil.copyProperties(spaceEditRequest, Space.class);
        //自动填充数据
        fillSpaceBySpaceLevel(space);
        //数据校验
        validSpace(space,false);
        //查数据库中是否有该图片
        Space olspace = getById(space.getId());
        ThrowUtils.throwIf(olspace == null, ErrorCode.NOT_FOUND_ERROR);

        //判断是否是本人或者管理员
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!olspace.getUserId().equals(loginUser.getId()) &&
                !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole()), ErrorCode.NO_AUTH_ERROR);

        //设置修改时间
        space.setEditTime(new Date());
        //操作数据库
        Boolean result = updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;
    }

    /**
     * 添加空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        //1.转化成picture并设置一些数值
        Space space = BeanUtil.copyProperties(spaceAddRequest, Space.class);

        //1.1设置默认值
        if(StrUtil.isBlank(spaceAddRequest.getSpaceName())){
            space.setSpaceName(loginUser.getUserName()+"的默认空间");
        }
        if(spaceAddRequest.getSpaceLevel()==null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }

        //1.2设置对应空间等级的容量
        fillSpaceBySpaceLevel(space);

        //2.校验参数
        validSpace(space,true);

        //3.权限校验，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        ThrowUtils.throwIf(SpaceLevelEnum.COMMON.getValue()!=space.getSpaceLevel()&&!userService.isAdmin(loginUser)
                , ErrorCode.NO_AUTH_ERROR,"无权限创建指定级别空间");

        //4.控制同一用户，只能创建一个空间，以及一个团队空间
        String lock=String.valueOf(userId).intern();
        synchronized (lock) {
           Long newSpaceId=  transactionTemplate.execute(status -> {
                //判断是否有空间
               boolean exists = lambdaQuery()
                       .eq(Space::getUserId, userId)
                       .eq(Space::getSpaceType, space.getSpaceType())
                       .exists();
               ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "不能重复创建空间，每个用户每类只能有一个");
                //创建空间
                boolean result = save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"数据库操作失败，请稍后再试");
               //创建成功后，如果是团队空间，关联新增团队成员记录
               if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                   SpaceUser spaceUser = new SpaceUser();
                   spaceUser.setSpaceId(space.getId());
                   spaceUser.setUserId(userId);
                   spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                   result = spaceUserService.save(spaceUser);
                   ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"创建团队成员失败");
               }
               //返回数据
               return space.getId();
            });

            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }


    /**
     * 参数校验
     *
     * @param space 图片类
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum enumByValue = SpaceTypeEnum.getEnumByValue(spaceType);
        //创建时校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "创建空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR, "创建空间类别不能为空");
        }
        //修改数据时，空间名称进行校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30, ErrorCode.PARAMS_ERROR, "空间名称过长");
        //修改数据时，空间级别进行校验
        ThrowUtils.throwIf(spaceLevel != null && spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不存在");
        //修改数据时，空间类别进行校验
        ThrowUtils.throwIf(spaceType != null && enumByValue == null, ErrorCode.PARAMS_ERROR, "空间类别不存在");
    }











}




