package com.yupi.yupicturebackend.service;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceEditRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceUpdateRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author MECHREVO
 * @description 针对表【space(空间)】的数据库操作Service
 * @createDate 2026-04-13 15:40:54
 */
public interface SpaceService extends IService<Space> {

    /**
     * 参数校验
     *
     * @param space 图片类
     * @param add   是否是创建时校验
     */
    public void validSpace(Space space, boolean add);

    /**
     * 获取分页查询（脱敏类）
     *
     * @param spaceQueryRequest
     * @param request
     * @return
     */
    Page<SpaceVO> getSpacePage(SpaceQueryRequest spaceQueryRequest, HttpServletRequest request);

    /**
     * 自动填充级别
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 编辑空间（管理员）
     * @param pictureUpdateRequest
     * @param request
     * @return
     */
    Boolean updateSpace(SpaceUpdateRequest pictureUpdateRequest, HttpServletRequest request);

    /**
     * 编辑空间（用户使用）
     * @param spaceEditRequest
     * @param request
     * @return
     */
    Boolean editSpace(SpaceEditRequest spaceEditRequest, HttpServletRequest request);


    /**
     * 添加私有空间
     */
    long addSpace(SpaceAddRequest spaceAddRequest , User loginUser);
}
