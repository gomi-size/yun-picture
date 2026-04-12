package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.yupi.yupicturebackend.common.AutoReviewParams;
import com.yupi.yupicturebackend.common.QueryWrapperUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.FileManager;
import com.yupi.yupicturebackend.manager.upload.PictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.URLUpload;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author MECHREVO
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2026-04-02 15:46:48
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;
    @Resource
    private UserService userService;
    @Resource
    private URLUpload uRLUpload;
    @Resource
    private PictureUpload pictureUpload;


    /**
     * 上传图片
     *
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.校验参数
        ThrowUtils.throwIf(inputSource == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        //2.判断是新增还是删除
        Picture picture = new Picture();

        //如果是更新，判断图片
        if (pictureUploadRequest.getId() != null) {
            //2.1前端有传递图片的id就将id赋值给picture
            picture.setId(pictureUploadRequest.getId());

            //2.2判断图片是否在数据库不在就直接抛出异常
            ThrowUtils.throwIf(getById(picture.getId()) == null, ErrorCode.NOT_FOUND_ERROR);

            //2.3（新增） 仅限管理员更新图片并且还要管理本人
            ThrowUtils.throwIf(!getById(picture.getId()).getUserId().equals(loginUser.getId()) && userService.isAdmin(loginUser)
                    , ErrorCode.NO_AUTH_ERROR);

        }
        //3.上传图片（无论更新还是添加这个时候都需要添加到云储存）
        //3.1。拼接路径，按id划分
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        //3.2.发送,获取返回结果
        //根据inputSource的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate=pictureUpload;
        if(inputSource instanceof String) {
            pictureUploadTemplate=uRLUpload;
        }

        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        //4.构造要入库的图片信息
        BeanUtil.copyProperties(uploadPictureResult, picture);

        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if(StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName=pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setUserId(loginUser.getId());

        //设置图片状态的审核
        AutoReviewParams.fillReviewParams(picture, loginUser);
        //5.操作数据库
        //5.1判断是更新还是删除
        if (picture.getId() != null) {
            //更新操作
            picture.setEditTime(new Date());
            boolean resultUpdate = updateById(picture);
            ThrowUtils.throwIf(!resultUpdate, ErrorCode.OPERATION_ERROR, "图片更新失败，请稍后再试");
        } else {
            //5.2添加
            boolean resultSave = save(picture);
            ThrowUtils.throwIf(!resultSave, ErrorCode.OPERATION_ERROR, "图片保存失败，请稍后再试");
        }
        return PictureVO.objToVo(picture);
    }

    /**
     * 获取图片分页的脱敏数据
     * @param pictureQueryRequest  图片分页DTO
     * @param request     用户session
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {

        //整个的一个流程: 获取分页初始数据->将数据脱敏->根据数据获取用户ID->查询id后转为Map集合->后将所有的用户转化为VO并存入到pictureVOList
        //->将数据放入pictureVOPage返回即可
        //0.获取picturePage
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = page(new Page<>(current, pageSize), QueryWrapperUtils.getQueryWrapper(pictureQueryRequest));

        //1.获取分页的初始数据
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        //这里修改
        if (CollectionUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        //2.获取到图片脱敏后的数据
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());

        //3.获取到用户id列表
        Set<Long> uerIdset = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());

        //4.进行数据库查询，后转换为map集合，使用用户id当key
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(uerIdset).stream().collect(Collectors.groupingBy(User::getId));

        //5.将每一个UserVO都填充到pictureVOList这个中
        pictureVOList.forEach(pictureVO -> {
            User user=null;
            Long userId = pictureVO.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                user= userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(BeanUtil.copyProperties(user, UserVO.class));
        });
        //6.将pictureVOList放入到pictureVOPage中就结束了
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }

    /**
     * 修改图片（管理员使用）
     * @param pictureUpdateRequest 图片修改DTO
     */
    @Override
    public Boolean updatePicture(PictureUpdateRequest pictureUpdateRequest,
                                 HttpServletRequest request) {
        //流程:将DTO转化为实体类->参数校验->操作数据库

        //参数转换(将DTO转化为实体类)
        Picture picture = BeanUtil.copyProperties(pictureUpdateRequest, Picture.class);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        //参数校验
        validPicture(picture);

        //查数据库中是否有该图片
        Picture oldPicture = getById(picture.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //设置图片的审核状态
        User loginUser = userService.getLoginUser(request);
        AutoReviewParams.fillReviewParams(picture, loginUser);

        //操作数据库
        boolean result = updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;
    }

    /**
     * 用户修改图片
     * @param pictureEditRequest 修改图片的DTO
     * @param request 用户
     * @return 布尔
     */
    @Override
    public Boolean editPicture(PictureEditRequest pictureEditRequest, HttpServletRequest request) {


        //流程:将DTO转化为实体类->参数校验->操作数据库
        //参数转换(将DTO转化为实体类)
        Picture oldPicture = BeanUtil.copyProperties(pictureEditRequest, Picture.class);
        oldPicture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));

        //参数校验
        validPicture(oldPicture);
        //查数据库中是否有该图片
        Picture picture = getById(oldPicture.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        //判断是否是本人或者管理员
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!picture.getUserId().equals(loginUser.getId()) &&
                !UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole()), ErrorCode.NO_AUTH_ERROR);

        //设置修改时间
        oldPicture.setEditTime(new Date());
        //设置图片状态的审核
        AutoReviewParams.fillReviewParams(picture, loginUser);
        //操作数据库
        Boolean result = updateById(oldPicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;
    }

    /**
     * 图片审核状态修改
     *
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.NOT_FOUND_ERROR);
        //1.2获取参数
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        //获取enumByValue防止前端乱传参数
        PictureReviewStatusEnum enumByValue = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        //2.3再一次判断，防止前端乱传参数
        ThrowUtils.throwIf(enumByValue == null || id == null, ErrorCode.PARAMS_ERROR);

        //2.判断图片是否存在
        Picture oldPicture = getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //2.1防止重复申查
        ThrowUtils.throwIf(oldPicture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR, "请勿重复审查");

        //3.修改数据
        Picture picture = BeanUtil.copyProperties(pictureReviewRequest, Picture.class);
        picture.setUserId(loginUser.getId());
        picture.setReviewTime(new Date());
        boolean result = updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 批量填充抓取图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        //1.校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count>30,ErrorCode.PARAMS_ERROR,"最多30条数据");
        //上传前缀默认等于关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if(StrUtil.isBlank(namePrefix)){
            namePrefix=searchText;
        }

        //2.抓取内容
        //2.1构建请求参数
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        //这里面存储的是一个html页面下面要对这个页面进行处理
        Document document;
        try {
            document= Jsoup.connect(fetchUrl).get();
        }catch (IOException e){
            log.error("获取页面失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"获取页面失败");
        }

        //3.解析内容
        //getElementsByClass("dgControl")是找到整个html中有dgControl盒子（div）
        Element div = document.getElementsByClass("dgControl").first();
        ThrowUtils.throwIf(ObjUtil.isEmpty(div), ErrorCode.OPERATION_ERROR,"获取元素失败");


        //再从盒子中找到关于img.mimg这个的元素，select是一个css选择器
        Elements imgElementList = div.select("img.mimg");

        //遍历元素
         int uploadCount = 0;
        for(Element imgElement :imgElementList)  {
            String fileUrl = imgElement.attr("src");
          if(StrUtil.isBlank(fileUrl)){
              log.info("链接为空，已跳过");
              continue;
          }

          //处理图片的地址，防止转义或者和对象存储冲突的问题
          int indexOf = fileUrl.indexOf("?");
          if(indexOf>-1){
              fileUrl = fileUrl.substring(0, indexOf);
          }
          //4.上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix+uploadCount+1);
            try {
                PictureVO pictureVO = uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，图片id={}",pictureVO.getId());
                uploadCount++;
            }catch (Exception e){
                log.error("图片上传失败");
                continue;
            }
            if(uploadCount>=count){
                break;
            }

        }
        return uploadCount;
    }


    /**
     * 参数校验
     *
     * @param picture 图片类
     */
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


}




