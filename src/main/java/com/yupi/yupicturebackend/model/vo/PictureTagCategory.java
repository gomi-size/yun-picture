package com.yupi.yupicturebackend.model.vo;

import com.yupi.yupicturebackend.model.entity.Picture;
import lombok.Data;

import java.util.List;

/**
 * 图片标签类
 */
@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 分类列表
     */
    private List<String> categoryList;
}
