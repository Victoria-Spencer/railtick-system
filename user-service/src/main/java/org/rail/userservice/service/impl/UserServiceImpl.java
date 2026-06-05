package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.BizException;
import org.rail.common.core.util.security.JwtTokenUtil;
import org.rail.common.core.util.security.PasswordCryptUtils;
import org.rail.userservice.mapper.UserMapper;
import org.rail.userservice.model.dto.UserLoginDTO;
import org.rail.userservice.model.dto.UserRegisterDTO;
import org.rail.userservice.model.dto.UserUpdateInfoDTO;
import org.rail.userservice.model.entity.User;
import org.rail.userservice.model.vo.UserInfoVO;
import org.rail.userservice.model.vo.UserUpdateVO;
import org.rail.userservice.model.vo.UserVO;
import org.rail.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 登录
     * @param userLoginDTO 登录信息
     * @return 登录成功的用户信息
     */
    @Override
    public UserVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.findByUsernameOrMailOrPhone(userLoginDTO.getUsernameOrMailOrPhone());

        if(user == null) {
            throw new BizException("用户不存在");
        }

        boolean isPasswordMatch = PasswordCryptUtils.match(userLoginDTO.getPassword(), user.getPassword());
        if(!isPasswordMatch) {
            throw new BizException("密码错误");
        }

        if (user.getIsDeleted() != null && user.getIsDeleted()) {
            userMapper.restoreUser(user.getId());
            user.setIsDeleted(false);
        }

        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        String token = JwtTokenUtil.createToken(user.getId(), user.getUsername());
        userVO.setAccessToken(token);

        return userVO;
    }

    /**
     * 退出登录
     */
    @Override
    public void logout() {
        // TODO 清除token
    }

    /**
     * 注册
     * @param userRegisterDTO 注册信息
     * @return 注册成功的用户信息
     */
    @Override
    @OperationLog(value = "用户注册", saveParam = true)
    public UserVO register(UserRegisterDTO userRegisterDTO) {
        User existingUser  = userMapper.findByUsernameOrMailOrPhone(userRegisterDTO.getUsername());
        if(existingUser != null) {
            throw new BizException("用户已经存在");
        }

        User user = new User();
        BeanUtil.copyProperties(userRegisterDTO, user);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setPassword(PasswordCryptUtils.encode(user.getPassword()));
        user.setIsDeleted(false);

        userMapper.insert(user);
        return BeanUtil.copyProperties(user, UserVO.class);
    }

    /**
     * 更新用户信息
     * @param userUpdateInfoDTO 更新信息
     * @return 更新后的用户信息
     */
    @Override
    @OperationLog(value = "修改用户信息", saveParam = true)
    public UserUpdateVO update(UserUpdateInfoDTO userUpdateInfoDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getUserId() == null) {
            throw new BizException("未获取到用户信息");
        }
        long userId = Long.parseLong(context.getUserId());
        userUpdateInfoDTO.setId(userId);

        User user = userMapper.getById(userId);
        if(user == null){
            throw new BizException("用户不存在，无法更新");
        }

        String oldPassword = userUpdateInfoDTO.getOldPassword();
        if (StrUtil.isNotBlank(oldPassword)) {
            boolean isPasswordMatch = PasswordCryptUtils.match(oldPassword, user.getPassword());
            if(!isPasswordMatch) {
                throw new BizException("原密码错误");
            }
        }

        BeanUtil.copyProperties(userUpdateInfoDTO, user, "password");
        user.setUpdateTime(LocalDateTime.now());
        String newPassword = userUpdateInfoDTO.getPassword();
        if (StrUtil.isNotBlank(newPassword)) {
            user.setPassword(PasswordCryptUtils.encode(newPassword));
        }
        userMapper.update(user);

        return BeanUtil.copyProperties(user, UserUpdateVO.class);
    }

    /**
     * 根据用户id查询用户信息
     */
    @Override
    public UserInfoVO query() {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getUserId() == null) {
            throw new BizException("未获取到用户信息");
        }

        long userId = Long.parseLong(context.getUserId());
        User user = userMapper.getById(userId);

        return BeanUtil.copyProperties(user, UserInfoVO.class);
    }

    /**
     * 根据用户id, 查询证件类型和证件件号
     * @return 证件类型和证件号
     */
    @Override
    @OperationLog(value = "查询用户证件类型和证件件号", saveParam = true)
    public UserIdCardDTO getIdCardInfoById() {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getUserId() == null) {
            throw new BizException("未获取到用户信息");
        }

        long userId = Long.parseLong(context.getUserId());
        User user = userMapper.getById(userId);

        return BeanUtil.copyProperties(user, UserIdCardDTO.class);
    }
}
