package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.QueryWrapperUtils;
import com.yupi.yupicturebackend.controller.UserController;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.FileManager;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.file.PictureEditRequest;
import com.yupi.yupicturebackend.model.dto.file.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.file.PictureUpdateRequest;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;
    @Resource
    private UserService userService;
    @Autowired
    private UserController user;

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        //1.校验参数
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        //2.判断是新增还是删除
        Picture picture = new Picture();
        if (pictureUploadRequest.getId() != null) {
            //2.1前端有传递图片的id就将id赋值给picture
            picture.setId(pictureUploadRequest.getId());
            //2.2判断图片是否在数据库不在就直接抛出异常
            ThrowUtils.throwIf(getById(picture.getId()) == null, ErrorCode.NOT_FOUND_ERROR);
        }
        //3.上传图片（无论更新还是添加这个时候都需要添加到云储存）
        //3.1。拼接路径，按id划分
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        //3.2.发送,获取返回结果
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        //4.构造要入库的图片信息
        BeanUtil.copyProperties(uploadPictureResult, picture);

        picture.setName(uploadPictureResult.getPicName());

        picture.setUserId(loginUser.getId());

        //5.操作数据库
        //5.1判断是更新还是删除
        if (picture.getId() != null) {
            //更新操作
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
        if (pictureList == null) {
            return pictureVOPage;
        }

        //2.获取到图片脱敏后的数据
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());

        //3.获取到用户id列表
        Set<Long> uerIDset = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());

        //4.进行数据库查询，后转换为map集合，使用用户id当key
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(uerIDset).stream().collect(Collectors.groupingBy(User::getId));

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
     * @param pictureUpdateRequest 图片修改DTO
     */
    @Override
    public Boolean updatePicture(PictureUpdateRequest pictureUpdateRequest) {
        //流程:将DTO转化为实体类->参数校验->操作数据库
        //参数转换(将DTO转化为实体类)
        Picture oldPicture = BeanUtil.copyProperties(pictureUpdateRequest, Picture.class);
        oldPicture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        //参数校验
        validPicture(oldPicture);
        //查数据库中是否有该图片
        Picture picture = getById(oldPicture.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        //操作数据库
        boolean result = updateById(oldPicture);
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

        //操作数据库
        Boolean result = updateById(oldPicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return result;
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




