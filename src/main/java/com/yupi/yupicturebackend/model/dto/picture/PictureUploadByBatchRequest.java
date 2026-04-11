package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

/**
 * 批量导入图片请求
 */
@Data
public class PictureUploadByBatchRequest {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;

    /**
     * 图片前缀
     */
    private String namePrefix;
}
