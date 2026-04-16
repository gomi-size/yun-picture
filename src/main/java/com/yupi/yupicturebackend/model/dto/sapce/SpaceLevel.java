package com.yupi.yupicturebackend.model.dto.sapce;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpaceLevel {

    /**
     * 值
     */
    private int value;
    /**
     * 中文
     */
    private String text;
    /**
     * 最大数量
     */
    private long maxCount;
    /**
     * 最大容量
     */
    private long maxSize;
}
