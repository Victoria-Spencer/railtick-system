package org.rail.userservice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.rail.userservice.model.dto.PsgrPageQueryDTO;
import org.rail.userservice.model.entity.Passenger;

import java.util.List;

@Mapper
public interface PassengerMapper {
    /**
     * 分页查询
     * @param psgrPageQueryDTO 分页查询参数
     * @return 分页结果
     */
    List<Passenger> query(PsgrPageQueryDTO psgrPageQueryDTO);

    /**
     * 根据用户id查询所有乘车人信息
     * @param userId 用户id
     * @return 乘车人列表
     */
    @Select("SELECT id, real_name, id_type, id_card," +
            " discount_type, phone, verify_status, create_time " +
            "FROM passenger " +
            "WHERE user_id = #{userId}")
    List<Passenger> getByUserId(Long userId);

    /**
     *
     * @param id 乘车人id
     * @return 乘车人信息
     */
    @Select("SELECT id, real_name, id_type, id_card," +
            " discount_type, phone, verify_status, create_time " +
            "FROM passenger " +
            "WHERE id = #{id}")
    Passenger getById(Long id);

    /**
     * 新增乘车人
     * @param passenger 乘车人信息
     */
    @Insert("insert into passenger(user_id, real_name, id_type, id_card," +
            " discount_type, phone, verify_status, create_time, update_time)" +
            " values(#{userId}, #{realName}, #{idType}, #{idCard}, #{discountType}," +
            " #{phone}, #{verifyStatus}, #{createTime}, #{updateTime})")
    void insert(Passenger passenger);

    /**
     * 更新乘车人信息
     * @param passenger 乘车人更新信息
     */
    @Update("update passenger " +
            "set phone = #{phone}, update_time = #{updateTime}" +
            "where id = #{id}")
    void updateById(Passenger passenger);

    /**
     * 根据ids移除乘车人
     * @param ids 乘车人id列表
     */
    void batchDelete(List<Long> ids);
}
