package com.v2board.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Select("SELECT COALESCE(SUM(commission_balance),0) FROM v2_order " +
            "WHERE status = #{status} AND commission_status = #{commissionStatus} AND invite_user_id = #{inviteUserId}")
    long selectSumCommissionBalance(@Param("inviteUserId") Long inviteUserId,
                                    @Param("status") int status,
                                    @Param("commissionStatus") int commissionStatus);
}

