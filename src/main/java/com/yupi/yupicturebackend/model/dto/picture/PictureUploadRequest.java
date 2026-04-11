package com.yupi.yupicturebackend.model.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;
    /**
     * 文件路径
     */
    private String fileUrl;
    /**
     * 图片名称
     */
    private String picName;

    private static final long serialVersionUID = 1L;
}
