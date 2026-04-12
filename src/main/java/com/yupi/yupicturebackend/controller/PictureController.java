package com.yupi.yupicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.QueryWrapperUtils;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureTagCategory;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {

    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //构建本地缓存
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        //需要判断用户是否登录
        User loginUser = userService.getLoginUser(request);
        //主要业务
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);

        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过rul上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        //得到用户
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        //主要业务
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);

        return ResultUtils.success(pictureVO);
    }

    /**
     * 删除图片
     *
     * @param deleteRequest 删除的统一请求
     * @param request       用户
     * @return 布尔
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
            , HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        //传递的参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        //对参数进行校验
        Picture picture = pictureService.getById(deleteRequest.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        //仅限用户本人或者管理员进行操作
        ThrowUtils.throwIf(!picture.getUserId().equals(loginUser.getId()) &&
                !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole()), ErrorCode.NO_AUTH_ERROR);

        //删除图片
        boolean result = pictureService.removeById(picture.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    /**
     * 修改图片（管理员使用）
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        Boolean result = pictureService.updatePicture(pictureUpdateRequest, request);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(result);
    }

    /**
     * 根据id获取图片(获取的是未脱敏的数据)
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }


    /**
     * 根据id获取图片(获取的是脱敏的数据)
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        PictureVO pictureVO = BeanUtil.copyProperties(picture, PictureVO.class);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 分页查询（获取的是未脱敏的数据，管理员使用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();

        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                QueryWrapperUtils.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页查询（获取是的脱敏的数据）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request) {
        //流程 获取redisKey（项目名+传递方法名+传递的参数名）->在redis中查询->(没有命中请数据库中查询，并保存到redis中，设置过期值)->命中了返回

        int size = pictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(size>20, ErrorCode.PARAMS_ERROR);
        //普通用户只能看到自己的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        //1.构建缓存key
        //1.2.将前端传递的类转化为json
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        //1.3使用MD5转换成hash值
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        //1.4拼接redis的key
        String cacheKey = String.format("yunpicture:listPictureVOByPage:%s", hashKey);

        // 先使用本地缓存
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if(cachedValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachePage);
        }

        //本地没有命中再从redis中查询,命中了那就返回
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);

        if(cachedValue != null) {
            //更新本地缓存
            LOCAL_CACHE.put(cacheKey,cachedValue);
            
            Page<PictureVO> cachePage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachePage);
        }

        //没有命中，从数据库查
        Page<PictureVO> pictureVOPage = pictureService.
                getPictureVOPage(pictureQueryRequest, request);
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        //设置缓存时间5~10分钟过期，防止缓存雪崩
        int cacheExpireTime=300+ RandomUtil.randomInt(0,300);
        //写到redis中
        opsForValue.set(cacheKey, cacheValue,cacheExpireTime, TimeUnit.SECONDS);
        //写到本地缓存中
        LOCAL_CACHE.put(cacheKey,cacheValue);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 修改图片（用户使用）
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null, ErrorCode.PARAMS_ERROR);
        Boolean result= pictureService.editPicture(pictureEditRequest,request);
        return ResultUtils.success(result);
    }


    /**
     * 获取标签
     *
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 审核图片（用户使用）
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }
    /**
     * 批量获取图片并创建图片
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureBatch(@RequestBody  PictureUploadByBatchRequest pictureUploadByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer uploadedCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadedCount);
    }

}
/**//**
 * 分页查询（获取是的脱敏的数据）
 *//*
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,HttpServletRequest request) {
        int size = pictureQueryRequest.getPageSize();
        ThrowUtils.throwIf(size>20, ErrorCode.PARAMS_ERROR);
        //普通用户只能看到自己的图片
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        //首先先取redis中查询
        //从数据库查
        Page<PictureVO> pictureVOPage = pictureService.
                getPictureVOPage(pictureQueryRequest, request);
        return ResultUtils.success(pictureVOPage);

    }*/