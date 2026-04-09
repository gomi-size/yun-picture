package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片审核请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PictureReviewRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;
    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核时间
     */
    private Date reviewTime;

    private static final long serialVersionUID = 1L;
}
