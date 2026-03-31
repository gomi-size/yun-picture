package com.yupi.yupicturebackend.model.vo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户方面（脱敏）
 *
 * @TableName user
 */
@TableName(value = "user")
@Data
public class UserVO {
    /**
     * id
     */
    private Long id;

    /**
     * 账号
     */
    private String userAccount;
    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    private String userRole;


    /**
     * 创建时间
     */
    private Date createTime;


}