package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.QueryWrapperUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.controller.UserController;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.user.UserLoginRequest;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.dto.user.UserRegisterRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static com.baomidou.mybatisplus.extension.toolkit.Db.page;

/**
 * @author MECHREVO
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-03-28 16:27:35
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    @Resource
    private UserMapper userMapper;


    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        //1.校验参数
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword, checkPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");

        ThrowUtils.throwIf(userAccount.length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号过短");

        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "密码过短");

        ThrowUtils.throwIf(!checkPassword.equals(userPassword),
                ErrorCode.PARAMS_ERROR, "两次密码不一样");
        //2.检查用户账号是否和数据库中已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        Long count = userMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.PARAMS_ERROR, "账号重复");
        //3.密码一定要加密
        String password = getEncryptPassword(userPassword);
        //4.插入数据到数据库中
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(password);
        user.setUserName("无名");
        boolean save = save(user);

        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "系统错误，请稍后重试");
        return user.getId();
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 用户登录
     * @param userLoginRequest 用户信息
     * @param request          Session
     * @return
     */
    @Override
    public LoginUserVO userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        //1.校验
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword),
                ErrorCode.PARAMS_ERROR, "参数为空");

        ThrowUtils.throwIf(userAccount.length() < 4,
                ErrorCode.PARAMS_ERROR, "用户账号过短");

        ThrowUtils.throwIf(userPassword.length() < 8,
                ErrorCode.PARAMS_ERROR, "密码过短");

        //2.查询数据库
        String password = getEncryptPassword(userPassword);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", password);
        User user = userMapper.selectOne(queryWrapper);
        ThrowUtils.throwIf(user==null,ErrorCode.PARAMS_ERROR,"没有该用户或密码错误");
        //3.给session赋值,计入用户登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        //4.返回LoginUserVO
        return BeanUtil.copyProperties(user, LoginUserVO.class);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        //1.取出用户校验用户
        Object userObject = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User user = (User) userObject ;
        ThrowUtils.throwIf(user==null||user.getId()==null,
                ErrorCode.NOT_LOGIN_ERROR);

        //从数据库中进行查询，进一步的校验
        user = userMapper.selectById(user.getId());
        ThrowUtils.throwIf(user==null,
                ErrorCode.NOT_LOGIN_ERROR);

        return user;
    }

    @Override
    public boolean Logout(HttpServletRequest request) {
        //1.取出用户校验用户
        Object user = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf(user==null,
                ErrorCode.NOT_LOGIN_ERROR);
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 分页查询列表
     * @param userQueryRequest
     * @return
     */
    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 1. 获取请求的页码和每页数量
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();

        // 2. 执行数据库分页查询 (获取 User 实体类数据)
        Page<User> userPage = page(new Page<>(current, pageSize),
                QueryWrapperUtils.getQueryWrapper(userQueryRequest));

        // 3. 初始化要返回的 UserVO 分页对象
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = new ArrayList<>();

        // 获取查询到的记录列表
        List<User> records = userPage.getRecords();

        // 4. 安全地进行非空判断 (先判断 != null，再判断 isEmpty)
        if (records != null && !records.isEmpty()) {
            // 遍历每一条 User 数据
            records.forEach(user -> {
                UserVO userVO = new UserVO();
                // 将单个 user 的属性拷贝到新创建的 userVO 中
                BeanUtil.copyProperties(user, userVO);
                // 将转换后的 userVO 加入到集合中
                userVOList.add(userVO);
            });
        }
        // 5. 将封装好的 List 放入分页对象中，并统一返回
        userVOPage.setRecords(userVOList);
        return userVOPage;
    }
}




