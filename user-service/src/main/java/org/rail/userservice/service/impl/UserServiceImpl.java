package org.rail.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.core.util.BeanUtils;
import org.rail.userservice.mapper.UserMapper;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.dto.UserRegisterDTO;
import org.rail.userservice.pojo.dto.UserUpdateInfoDTO;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserVO;
import org.rail.userservice.service.UserService;
import org.rail.userservice.util.JwtTokenUtil;
import org.rail.userservice.util.MD5Util;
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
        // 根据用户名查询用户信息
        User user = userMapper.findByUsernameOrMailOrPhone(userLoginDTO.getUsernameOrMailOrPhone());

        // 判断用户是否存在
        if(user == null) {
            throw new BusinessException("用户不存在");
        }


        // md5加密
        String password = userLoginDTO.getPassword();
        password = MD5Util.encrypt(password);

        // 校验密码
        if(!user.getPassword().equals(password)) {
            throw new BusinessException("密码错误");
        }

        // 封装返回用户信息
        UserVO userVO = BeanUtils.copyProperties(user, UserVO.class);

        // 生成令牌
        String token = JwtTokenUtil.createToken(user.getId());
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
    public UserVO register(UserRegisterDTO userRegisterDTO) {
        // 根据用户名查询用户信息
        User existingUser  = userMapper.findByUsernameOrMailOrPhone(userRegisterDTO.getUsername());
        if(existingUser != null) {
            throw new BusinessException("用户已经存在");
        }

        // 拷贝信息
        User user = new User();
        BeanUtils.copyProperties(userRegisterDTO, user);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // md5加密
        String password = user.getPassword();
        user.setPassword(MD5Util.encrypt(password));

        // 添加到数据库
        userMapper.insert(user);
        return BeanUtils.copyProperties(user, UserVO.class);
    }

    /**
     * 更新用户信息
     * @param userUpdateInfoDTO 更新信息
     * @return 更新后的用户信息
     */
    @Override
    public UserVO update(UserUpdateInfoDTO userUpdateInfoDTO) {
        // 根据用户名查询用户信息
        User user = userMapper.findByUsernameOrMailOrPhone(userUpdateInfoDTO.getUsername());
        BeanUtils.copyProperties(userUpdateInfoDTO, user);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
        return BeanUtils.copyProperties(user, UserVO.class);
    }

    /**
     * 根据用户id查询用户信息
     */
    @Override
    public User getById(Long userId) {
        return userMapper.getById(userId);
    }

    /**
     * 查询证类型和证件件号
     * @param id 用户id
     * @return 证件类型和证件号
     */
    @Override
    public UserIdCardDTO getIdCardInfoById(Long id) {
        User user = userMapper.getById(id);
        return BeanUtil.copyProperties(user, UserIdCardDTO.class);
    }
}
