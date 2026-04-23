package com.yupi.yupicturebackend.model.dto.sapce.analyze;

import lombok.Data;

import java.io.Serializable;

/**
 * 获取排行榜
 */
@Data
public class SpaceRankAnalyzeRequest implements Serializable {

    /**
     * 排名前 N 的空间
     */
    private Integer topN = 10;

    private static final long serialVersionUID = 1L;
}
