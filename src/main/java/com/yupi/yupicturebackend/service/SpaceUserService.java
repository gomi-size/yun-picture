package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserAddRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserEditRequest;
import com.yupi.yupicturebackend.model.dto.sapceUser.SpaceUserQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceUserVO;
import com.yupi.yupicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author MECHREVO
 * @description 针对表【space_user(空间用户关联)】的数据库操作Service
 * @createDate 2026-04-24 15:54:39
 */
public interface SpaceUserService extends IService<SpaceUser> {


    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add
     */
    public void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员查询（列表）
     *
     * @param spaceUserQueryRequest
     * @return
     */
    List<SpaceUserVO> getSpaceVOList(SpaceUserQueryRequest spaceUserQueryRequest);


    /**
     * 添加成员到空间
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 编辑空间成员信息（主要是编辑这个成员在这个空间角色）
     *
     * @param spaceUserEditRequest
     */
    void editSpaceUser(SpaceUserEditRequest spaceUserEditRequest);
}
