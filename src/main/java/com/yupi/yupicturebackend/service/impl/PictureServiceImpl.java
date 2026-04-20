package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yupicturebackend.Utils.ColorSimilarUtils;
import com.yupi.yupicturebackend.Utils.QueryWrapperUtils;
import com.yupi.yupicturebackend.api.aliyunAi.aliyunAiApi;
import com.yupi.yupicturebackend.api.aliyunAi.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunAi.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.common.*;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.FileManager;
import com.yupi.yupicturebackend.manager.upload.PictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.URLUpload;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;


import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    @Autowired
    private CosManager cosManager;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private aliyunAiApi  aliyunAiApi;
    //构建本地缓存
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    private final String VERSION= "yunpicture:list_version";
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
        //1.1校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if(spaceId != null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
            //必须是创建人才能上传
            ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()), ErrorCode.NOT_FOUND_ERROR,"没有空间权限");
            //校验额度
            if (space.getTotalCount() > space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() > space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        //2.判断是新增还是删除
        Picture picture = new Picture();
        //如果是更新，判断图片
        if (pictureUploadRequest.getId() != null) {
            //2.1前端有传递图片的id就将id赋值给picture
            picture.setId(pictureUploadRequest.getId());

            //2.2判断图片是否在数据库不在就直接抛出异常
            ThrowUtils.throwIf(getById(picture.getId()) == null, ErrorCode.NOT_FOUND_ERROR);

            //2.3（新增） 仅本人或者是管理员
            ThrowUtils.throwIf(!getById(picture.getId()).getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)
                    , ErrorCode.NO_AUTH_ERROR);
            //2.4（新增）删除cos对象存储中的图片
            clearPictureFile(picture);

            //校验空间是否一致
            //没传spaceId，则复用原有的spaceId
            if(spaceId==null){
                if(getById(picture.getId()).getSpaceId()!=null){
                    spaceId=getById(picture.getId()).getSpaceId();
                }
            }else {
                //传递了那么，必须和原图片空间一样
                ThrowUtils.throwIf(ObjUtil.notEqual(spaceId,getById(picture.getId()).getSpaceId()), ErrorCode.PARAMS_ERROR,"两次id不一样");
            }
        }
        //3.上传图片（无论更新还是添加这个时候都需要添加到云储存）
        //3.1。拼接路径，按id划分=>该为空间
        String uploadPathPrefix;
        if(spaceId==null){
            //公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        }else {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }


        //3.2.发送,获取返回结果
        //根据inputSource的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate=pictureUpload;
        if(inputSource instanceof String) {
            pictureUploadTemplate=uRLUpload;
        }

        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        //4.构造要入库的图片信息（这里不会赋值id）
        BeanUtil.copyProperties(uploadPictureResult, picture);

        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if(StrUtil.isNotBlank(pictureUploadRequest.getPicName())){
            picName=pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setUserId(loginUser.getId());
        picture.setSpaceId(spaceId);

        //设置图片状态的审核
        AutoReviewParams.fillReviewParams(picture, loginUser);
        //5.操作数据库,使用事务 TODO 这里还需要进行对老图片删除
        transactionTemplate.execute(status -> {
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
            //5.2更新使用空间的额度
            if(picture.getSpaceId()!=null){
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, picture.getSpaceId())
                        .setSql("totalSize=totalSize+" + picture.getPicSize())
                        .setSql("totalCount=totalCount+1")
                        .update();
                ThrowUtils.throwIf(!update,ErrorCode.OPERATION_ERROR,"额度更新失败");
            }
            return PictureVO.objToVo(picture);
        });
        return PictureVO.objToVo(picture);
    }


    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    @Override
    public Boolean deletePicture(DeleteRequest deleteRequest
            , HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        //传递的参数校验
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        //对参数进行校验
        Picture oldPicture = getById(deleteRequest.getId());
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //校验权限
        checkPictureAuth(loginUser, oldPicture);
        Long spaceId = oldPicture.getSpaceId();
        //5.操作数据库,使用事务 TODO 这里还需要进行对老图片删除
        transactionTemplate.execute(status -> {
            //删除图片
            boolean result = removeById(oldPicture.getId());
            if (spaceId != null) {
                //更新使用空间的额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalSize=totalSize-" + oldPicture.getPicSize())
                        .setSql("totalCount=totalCount-1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }

            return result;
        });

        //清理图片资源
        clearPictureFile(oldPicture);
        return true;
    }

    /**
     * 颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        //1.校验参数
        ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        //2.校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        //3.查询该空间下的所有图片
        List<Picture> pictureList = lambdaQuery().eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor).list();
        //3.1如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        //3.2将目标颜色转换为主色调
        Color targetColor = Color.decode(picColor);
        //4.计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                //进行排序处理，最相似的最前
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    //没有主色调排在最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)
                .collect(Collectors.toList());
        //5.返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        //1.获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();

        ThrowUtils.throwIf(pictureIdList == null || pictureIdList.size() <= 0, ErrorCode.PARAMS_ERROR);
        //允许管理员进行编辑所有的图片
        //2.校验空间权限
        List<Picture> pictureList;
        if(spaceId!=null){
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space==null, ErrorCode.NOT_FOUND_ERROR);
            ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
            //3.查询指定的图片
             pictureList = lambdaQuery().select(Picture::getId, Picture::getSpaceId)
                    .eq(Picture::getSpaceId, spaceId)
                    .in(Picture::getId, pictureIdList).list();
             if (CollUtil.isEmpty(pictureList)) {
                 return;
             }
        }else{
            //3.查询指定的图片
             pictureList= lambdaQuery().select(Picture::getId)
                    .in(Picture::getId, pictureIdList).list();
            if (CollUtil.isEmpty(pictureList)) {
                return;
            }
        }
        //4.更新分类和标签
        pictureList.forEach(picture -> {
            if(category!=null){
                picture.setCategory(category);
            }
            picture.setUpdateTime(new Date());
            if(tags!=null){
                picture.setTags(tags.toString());
            }
        });
        //批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList,nameRule);
        //5.操作 数据库进行批量更新
        boolean result = updateBatchById(pictureList);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"数据库更新失败");

    }

    /**
     * 创建扩图请求
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        //1.校验参数
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest==null, ErrorCode.PARAMS_ERROR);
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = getById(pictureId);
        ThrowUtils.throwIf(picture==null, ErrorCode.NOT_FOUND_ERROR);
        checkPictureAuth(loginUser,picture);
        //2.构建参数
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest=new CreateOutPaintingTaskRequest();
        //3.放入input
        CreateOutPaintingTaskRequest.Input input=new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        //4.放入Parameters
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        //5.创建任务
        return aliyunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);

    }

    /**
     nameRule格式:图片{序号}
     @param pictureList
     @param nameRule
    **/
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if(StrUtil.isBlank(nameRule)){
            return;
        }
        long count=1;
        try{
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        }catch (Exception e){
            log.error("名称解析失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"名称解析失败");
        }

    }

    /**
     * 获取图片分页的脱敏数据
     * @param pictureQueryRequest  图片分页DTO
     * @param request     用户session
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {

        //1.校验权限
        Long spaceId = pictureQueryRequest.getSpaceId();
        if(spaceId==null){
            //1.1公开图库，普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpace(true);
        }else{
            //1.2私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space==null, ErrorCode.NOT_FOUND_ERROR,"没有该空间");
            ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()), ErrorCode.NO_AUTH_ERROR);

        }
        //2.使用缓存
        //流程 获取redisKey（项目名+传递方法名+传递的参数名）->在redis中查询->
        // (没有命中请数据库中查询，并保存到redis中，设置过期值)->命中了返回
        //2.1设置版本
        String version = stringRedisTemplate.opsForValue().get(VERSION);
        if (version == null) {
            version = "1";
            stringRedisTemplate.opsForValue().set(VERSION, version);
        }

        //2.2构建缓存key
        //将前端传递的类转化为json
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        //使用MD5转换成hash值
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        //拼接redis的key
        String cacheKey = String.format("yunpicture:listPictureVOByPage:%s/%s", hashKey,version);

        //2.3查寻缓存（先使用本地缓存）
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if(cachedValue != null) {
            Page<PictureVO> cachePage = JSONUtil.toBean(cachedValue, Page.class);
            return cachePage;
        }

        //2.4本地没有命中再从redis中查询,命中了那就返回
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        //2.5本地缓存没有，要是redis有的话就要设置到本地缓存
        if(cachedValue != null) {
            //更新本地缓存
            LOCAL_CACHE.put(cacheKey,cachedValue);

            Page<PictureVO> cachePage = JSONUtil.toBean(cachedValue, Page.class);
            return cachePage;
        }
        //3.进行数据库的查询
        //整个的一个流程: 获取分页初始数据->将数据脱敏->根据数据获取用户ID->查询id后转为Map集合->后将所有的用户转化为VO并存入到pictureVOList
        //->将数据放入pictureVOPage返回即可
        //3.1获取picturePage
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = page(new Page<>(current, pageSize), QueryWrapperUtils.getQueryWrapper(pictureQueryRequest));

        //3.2获取分页的初始数据
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        //这里修改
        if (CollectionUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        //3.3获取到图片脱敏后的数据
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());

        //3.4获取到用户id列表
        Set<Long> uerIdset = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());

        //3.5进行数据库查询，后转换为map集合，使用用户id当key
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(uerIdset).stream().collect(Collectors.groupingBy(User::getId));

        //3.6将每一个UserVO都填充到pictureVOList这个中
        pictureVOList.forEach(pictureVO -> {
            User user=null;
            Long userId = pictureVO.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                user= userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(BeanUtil.copyProperties(user, UserVO.class));
        });
        //3.7将pictureVOList放入到pictureVOPage中就结束了
        pictureVOPage.setRecords(pictureVOList);

        //4.查询后需要存储缓存中
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        //设置缓存时间5~10分钟过期，防止缓存雪崩
        int cacheExpireTime=300+ RandomUtil.randomInt(0,300);
        //写到redis中
        opsForValue.set(cacheKey, cacheValue,cacheExpireTime, TimeUnit.SECONDS);
        //写到本地缓存中
        LOCAL_CACHE.put(cacheKey,cacheValue);

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
        User loginUser = userService.getLoginUser(request);
        //参数校验
        validPicture(oldPicture);
        checkPictureAuth(loginUser,oldPicture);
        //查数据库中是否有该图片
        Picture picture = getById(oldPicture.getId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);

        //判断是否是本人或者管理员

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
            pictureUploadRequest.setPicName(namePrefix+(uploadCount+1));
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
     * 删除对象存储的图片
     * @param olPicture 图片
     */
    @Async
    @Override
    public void clearPictureFile(Picture olPicture) {
        //1.查看这个图片中谁还在使用
        Long count = lambdaQuery().eq(Picture::getUrl, olPicture.getUrl()).count();
        if(count>1){
            //有多条记录,不清理
            return;
        }
        //删除图片
        cosManager.deleteObject(olPicture.getUrl());
        //删除缩略图
        if(StrUtil.isNotBlank(olPicture.getThumbnailUrl())){
            cosManager.deleteObject(olPicture.getThumbnailUrl());
        }
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

    /**
     * 校验空间图片的权限
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture){
        Long spaceId = picture.getSpaceId();
        Long userId = loginUser.getId();
        if(spaceId==null){
            //表示公共图库，仅本人或者管理员可以操作
            ThrowUtils.throwIf(!picture.getUserId().equals(userId)&&!userService.isAdmin(loginUser),ErrorCode.NO_AUTH_ERROR);
        }else {
            //代表的是私有图库,只能本人操作
            ThrowUtils.throwIf(!picture.getUserId().equals(userId),ErrorCode.NO_AUTH_ERROR);
        }
    }


}




