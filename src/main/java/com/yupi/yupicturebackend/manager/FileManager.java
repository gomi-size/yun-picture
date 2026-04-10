package com.yupi.yupicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.yupi.yupicturebackend.config.CosClientConfig;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 与业务有点关系的类（也是通用的）
 */
/**
 * 文件服务
 * @deprecated 已废弃，改为使用 upload 包的模板方法优化
 */
@Deprecated
@Slf4j
@Service
public class FileManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private CosManager cosManager;

    /**
     * 上传图片
     *
     * @param multipartFile    文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        //1.校验图片
        validPicture(multipartFile);
        //2.图片上传地址
        //2.1使用RandomUtil生成16位的随机字符串
        String uuid = RandomUtil.randomString(16);

        //2.2得到文件的名字包括后缀名
        String originalFilename = multipartFile.getOriginalFilename();

        //2.3得到后缀
        String suffix = FileUtil.getSuffix(originalFilename);

        //2.4拼接字符串，设置上传路径
        String uploadFileName = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, suffix);

        //uploadPathPrefix这个是key是用户自己创建的文件夹
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFileName);

        //3.解析结果并返回
        File file = null;
        try {
            // 3.1。在本地服务器中创建一个空间file拿到的是路径
            file = File.createTempFile(uploadPath, null);

            //3.2.将文件放入这个空间中
            multipartFile.transferTo(file);

            //3.3得到返回的对象
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 3.4.获取图片信息对象
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            //4.返回封装结果

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
            return uploadPictureResult;

        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            //4.清理临时文件
            deleteTempFile(file, uploadPath);
        }
    }

    private void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件为空");
        //1.校验文件大小
        long fileSize = multipartFile.getSize();
        final long ONE_N = 1024 * 1024;
        ThrowUtils.throwIf(fileSize > ONE_N, ErrorCode.PARAMS_ERROR, "文件太大");
        //2.校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        //2.1允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jepg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), ErrorCode.PARAMS_ERROR, "文件格式错误");

    }

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
