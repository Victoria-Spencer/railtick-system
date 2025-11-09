package org.rail.userservice.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.rail.userservice.pojo.dto.PsgrPageQueryDTO;
import org.rail.userservice.pojo.dto.PsgrUpdateDTO;
import org.rail.userservice.pojo.entity.Passenger;

import java.util.List;

@Mapper
public interface PassengerMapper {
    /**
     * 分页查询
     * @param psgrPageQueryDTO
     * @return
     */
    List<Passenger> query(PsgrPageQueryDTO psgrPageQueryDTO);

    /**
     *
     * @param id
     * @return
     */
    @Select("SELECT id, real_name, id_type, id_card," +
            " discount_type, phone, verify_status, create_time " +
            "FROM passenger " +
            "WHERE id = #{id}")
    Passenger getById(Long id);

    /**
     * 新增乘车人
     * @param passenger
     */
    @Insert("insert into passenger(user_id, real_name, id_type, id_card," +
            " discount_type, phone, verify_status, create_time, update_time)" +
            " values(#{userId}, #{realName}, #{idType}, #{idCard}, #{discountType}," +
            " #{phone}, #{verifyStatus}, #{createTime}, #{updateTime})")
    void insert(Passenger passenger);

    /**
     * 更新乘车人信息
     * @param passenger
     */
    @Update("update passenger " +
            "set phone = #{phone}, update_time = #{updateTime}" +
            "where id = #{id}")
    void updateById(Passenger passenger);

    /**
     * 根据ids移除乘车人
     * @param ids
     */
    void batchDelete(List<Long> ids);
}
