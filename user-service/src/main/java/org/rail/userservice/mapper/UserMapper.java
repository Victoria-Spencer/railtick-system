package org.rail.userservice.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserVO;

@Mapper
public interface UserMapper {

    /**
     * 根据id查询用户信息
     * @param usernameOrMailOrPhone
     * @return
     */
    User findByUsernameOrMailOrPhone(String usernameOrMailOrPhone);

    /**
     * 新增用户
     * @param user
     * @return
     */
    @Insert("insert into" +
            " user(username, real_name, password, " +
            "id_type, id_card, phone, email, create_time, update_time)" +
            " values(#{username}, #{realName}, #{password}, #{idType}, " +
            "#{idCard}, #{phone}, #{email}, #{createTime}, #{updateTime})")
    void insert(User user);

    /**
     * 更新用户信息
     * @param user
     */
    void update(User user);
}
