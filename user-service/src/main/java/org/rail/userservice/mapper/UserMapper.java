package org.rail.userservice.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.rail.userservice.pojo.entity.User;

@Mapper
public interface UserMapper {

    /**
     * 根据id查询用户信息
     * @param usernameOrMailOrPhone
     * @return
     */
    User findByUsernameOrMailOrPhone(String usernameOrMailOrPhone);
}
