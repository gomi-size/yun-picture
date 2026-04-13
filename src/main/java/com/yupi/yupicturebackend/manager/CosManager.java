package com.yupi.yupicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yupi.yupicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用的文件处理类
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    // ... 一些操作 COS 的方法


    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 下载对象
     *
     * @param key 唯一键（是文件夹加上文件的名字）
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     *             必须要添加图片处理操作否者会报错
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        //（获取基本信息也被视作为一种处理）picOperations图片处理对象
        // 对图片进行处理
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);

        //可能有多个规则，利用一个集合，进行存储
        List<PicOperations.Rule> ruleList=new ArrayList<>();

        //一，文件压缩（转化为webp格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        //设置参数
        PicOperations.Rule compressRule=new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        ruleList.add(compressRule);

        //二，缩略图处理,仅对大于20k的才进行压缩
        if (file.length()>2*1024){
            PicOperations.Rule thumbnailRule=new PicOperations.Rule();
            //拼接上传图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail."+FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>",256,256));
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            ruleList.add(thumbnailRule);
        }
        //将规则设置到图片处理规则中
        picOperations.setRules(ruleList);
        // 图片处理规则putObjectRequest把图片处理对象放入到这个类中
        putObjectRequest.setPicOperations(picOperations);

        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

}
