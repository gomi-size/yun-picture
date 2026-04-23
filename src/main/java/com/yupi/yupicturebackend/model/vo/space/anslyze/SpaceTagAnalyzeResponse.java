package com.yupi.yupicturebackend.model.vo.space.anslyze;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 空间标签分类分析的响应类
 */
public class SpaceTagAnalyzeResponse implements Serializable {

    /**
     * 标签名称
     */
    private String tag;

    /**
     * 使用次数
     */
    private Long count;

    private static final long serialVersionUID = 1L;

}
