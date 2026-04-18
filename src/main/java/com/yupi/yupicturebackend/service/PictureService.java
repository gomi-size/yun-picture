package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.dto.sapce.SpaceAddRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;


/**
 * @author MECHREVO
 * @description 针对表【picture(图片)】的数据库操作Service
 * @createDate 2026-04-02 15:46:48
 */
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     *
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);


    /**
     * 分页查询(用户使用)
     *
     * @param pictureQueryRequest 图图片分页DTO
     * @param request     用户session
     * @return PictureVO分页
     */
    Page<PictureVO> getPictureVOPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request);


    /**
     * 修改图片（管理员使用）
     *
     * @param pictureUpdateRequest 图片修改DTO
     */
    Boolean updatePicture(PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request);

    /**
     *  修改图片（用户使用）
     * @param pictureEditRequest 修改图片的DTO
     * @param request 用户
     * @return 布尔
     */
    Boolean editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request);

    /**
     * 图片审核状态修改
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 批量填充抓取图片
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest
            , User loginUser);

    /**
     * 删除对象存储的图片
     * @param picture 图片
     */
    void clearPictureFile(Picture picture);

    /**
     * 校验权限
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    Boolean deletePicture(DeleteRequest deleteRequest
            , HttpServletRequest request);

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 批量编辑图片
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);
}
