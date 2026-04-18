package com.yupi.yupicturebackend.manager.upload;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模板是一个抽象类
 */
@Component
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     *
     * @param inputSource    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        //1. 校验图片
        validPicture(inputSource);
        //2.获取图片上传地址
        //2.1使用RandomUtil生成16位的随机字符串
        String uuid = RandomUtil.randomString(16);

        //2.2 得到文件的名字包括后缀名
        String originalFilename = getOriginalFilename(inputSource);

        //2.3得到后缀
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(originalFilename),
                "jpg");

        //2.4拼接字符串，设置上传路径
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, suffix);

        //uploadPathPrefix这个是key是用户自己创建的user文件夹
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);

        //3.解析结果并返回
        File file = null;
        try {
            // 3.1.在本地服务器中创建一个空间file拿到的是路径
            file = File.createTempFile(uploadPath, null);
            
            // 3.2 .处理输入源并生成本地临时文件
            processFile(inputSource,file);
            
            //3.3上传到对象存储并得到返回的对象
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 3.4.获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            //3.5获取图片处理结果
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            //这个是取出在CosManager中定义的规则的图片
            List<CIObject> objectList = processResults.getObjectList();

            if(CollectionUtil.isNotEmpty(objectList)){
                //3.6获取压缩之后文件信息
                CIObject compressdCiobject = objectList.get(0);
                //这个是缩略图
                CIObject thumbnailCiobject=compressdCiobject;

                //有缩略图才进行获取，没有就直接等于原图
                if(objectList.size()>1){
                    thumbnailCiobject= objectList.get(1);
                }
                //返回压缩图的返回结果
                return buildResult(originalFilename, compressdCiobject,thumbnailCiobject,imageInfo);
            }
            //4.处理图片信息并封装返回结果
            return buildResult( uploadPath, originalFilename, file, imageInfo);

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            //5.清理临时文件
            deleteTempFile(file, uploadPath);
        }
    }

    /**
     * 封装返回结果（有压缩图的封装返回结果）
     * @param originalFilename 原始文件名
     * @param compressdCiobject 压缩后的对象
     * @param thumbnailCiobject 缩略图的对象
     * @param imageInfo 图片信息
     *
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, CIObject compressdCiobject, CIObject thumbnailCiobject,
                                            ImageInfo imageInfo) {
        int pictureWidth = compressdCiobject.getWidth();
        int pictureHeight = compressdCiobject.getHeight();

        //得到宽高比
        double picScale = NumberUtil.round(pictureWidth * 1.0 / pictureHeight, 2).doubleValue();


        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        //绝对路径
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressdCiobject.getKey());
        //得到主名字
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        //得到大小
        uploadPictureResult.setPicSize(compressdCiobject.getSize().longValue());
        //得到宽度
        uploadPictureResult.setPicWidth(pictureWidth);
        //得到高度
        uploadPictureResult.setPicHeight(pictureHeight);
        //得到宽高比
        uploadPictureResult.setPicScale(picScale);
        //新增缩略图,取出Url的方法和compressdCiobject这个一样
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" +thumbnailCiobject.getKey());
        //得到格式
        uploadPictureResult.setPicFormat(compressdCiobject.getFormat());
        //得到主色调
        uploadPictureResult.setPicColor(imageInfo.getAve());
        return uploadPictureResult;

    }

    /**
     * 封装返回结果
     * @param uploadPath 上传的路径
     * @param originalFilename 图片的主名字
     * @param file 上传的文件
     * @param imageInfo 对象存储服务返回的图片信息
     * @return
     */
    private UploadPictureResult buildResult(String uploadPath, String originalFilename, File file, ImageInfo imageInfo) {
        int pictureWidth = imageInfo.getWidth();
        int pictureHeight = imageInfo.getHeight();

        //得到宽高比
        double picScale = NumberUtil.round(pictureWidth * 1.0 / pictureHeight, 2).doubleValue();


        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        //绝对路径
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        //得到主名字
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        //得到大小
        uploadPictureResult.setPicSize(FileUtil.size(file));
        //得到宽度
        uploadPictureResult.setPicWidth(pictureWidth);
        //得到高度
        uploadPictureResult.setPicHeight(pictureHeight);
        //得到宽高比
        uploadPictureResult.setPicScale(picScale);
        //得到格式
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        //得到主色调
        uploadPictureResult.setPicColor(imageInfo.getAve());

        return uploadPictureResult;
    }

    /**
     * 处理输入源并生成本地临时文件
     * @param inputSource
     */
    protected abstract void processFile(Object inputSource, File file) throws IOException; ;

    /**
     * 获取输入源的原始文件名
     * @param inputSource
     * @return
     */
    protected abstract String getOriginalFilename(Object  inputSource ) ;

    /**
     * 校验输入源
     * @param inputSource
     */
    protected abstract void validPicture(Object inputSource) ;


    /**
     * 清理临时文件
     *
     * @param file
     * @param uploadPath
     */
    public static void deleteTempFile(File file, String uploadPath) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean delete = file.delete();
        if (!delete) {
            log.error("file delete error, filepath = {}", uploadPath);
        }

    }


}
