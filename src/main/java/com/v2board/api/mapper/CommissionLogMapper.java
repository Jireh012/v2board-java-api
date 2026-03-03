package com.v2board.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.model.CommissionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CommissionLogMapper extends BaseMapper<CommissionLog> {

    @Select("SELECT COALESCE(SUM(get_amount),0) FROM v2_commission_log WHERE invite_user_id = #{inviteUserId}")
    long selectSumGetAmount(@Param("inviteUserId") Long inviteUserId);
}

