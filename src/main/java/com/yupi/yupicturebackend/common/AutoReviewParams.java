package com.yupi.yupicturebackend.common;


import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;

import java.util.Date;

/**
 * 审核通用类
 */
public class AutoReviewParams {
    public static void fillReviewParams(Picture picture, User user) {
        if (user.getUserRole().equals(UserRoleEnum.ADMIN.getValue())) {
            //管理员能自动通过审核
            picture.setReviewTime(new Date());
            picture.setReviewerId(user.getId());
            picture.setReviewMessage("管理员能自动通过审核");
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            //不是管理员改为未审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }
}
