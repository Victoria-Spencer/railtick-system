package org.rail.userservice.service.impl;

import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.utils.BeanUtils;
import org.rail.userservice.mapper.UserMapper;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.dto.UserRegisterDTO;
import org.rail.userservice.pojo.dto.UserUpdateInfoDTO;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserVO;
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
     * @param userLoginDTO
     * @return
     */
    public UserVO login(UserLoginDTO userLoginDTO) {
        // 根据用户名查询用户信息
        User user = userMapper.findByUsernameOrMailOrPhone(userLoginDTO.getUsernameOrMailOrPhone());

        // 判断用户是否存在
        if(user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验密码
        if(!user.getPassword().equals(userLoginDTO.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 封装返回用户信息
        UserVO userVO = BeanUtils.copyProperties(user, UserVO.class);

        // TODO 令牌校验
        userVO.setAccessToken("1");

        return userVO;
    }

    /**
     * 退出登录
     */
    public void logout() {
        // TODO 清除token
    }

    /**
     * 注册
     * @param userRegisterDTO
     * @return
     */
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

        // 添加到数据库
        userMapper.insert(user);
        return BeanUtils.copyProperties(user, UserVO.class);
    }

    /**
     * 更新用户信息
     * @param userUpdateInfoDTO
     * @return
     */
    public UserVO update(UserUpdateInfoDTO userUpdateInfoDTO) {
        // 根据用户名查询用户信息
        User user = userMapper.findByUsernameOrMailOrPhone(userUpdateInfoDTO.getUsername());
        BeanUtils.copyProperties(userUpdateInfoDTO, user);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
        return BeanUtils.copyProperties(user, UserVO.class);
    }

    /**
     * 根据用户名查询用户信息
     * @param username
     * @return
     */
    public User findByUsername(String username) {
        return userMapper.findByUsernameOrMailOrPhone(username);
    }
}
