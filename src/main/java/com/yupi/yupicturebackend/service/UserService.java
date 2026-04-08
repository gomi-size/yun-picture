package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.model.dto.user.UserLoginRequest;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.dto.user.UserRegisterRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author MECHREVO
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2026-03-28 16:27:35
 */
public interface UserService extends IService<User> {

    /**
     * 用户注释
     *
     * @param userRegisterRequest
     * @return 用户id
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 加密密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登入
     *
     * @param userLoginRequest 用户信息
     * @param request          Session
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取用户登录信息
     * @param request Session
     * @return 用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户退出
     * @param request session
     * @return Boolean
     */
    boolean Logout(HttpServletRequest request);

    /**
     * 分页查询用户列表
     * @param userQueryRequest
     * @return
     */
    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     *
     * @param user 用户
     * @return
     */
    boolean isAdmin(User user);

}
