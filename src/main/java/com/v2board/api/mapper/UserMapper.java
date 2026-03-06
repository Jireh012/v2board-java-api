package com.v2board.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 批量更新用户流量 (CASE UPDATE)，对齐 PHP traffic:update
     * @param trafficMap key=userId, value=long[]{u_increment, d_increment}
     * @param timestamp  当前时间戳
     */
    int batchUpdateTraffic(@Param("trafficMap") Map<Long, long[]> trafficMap, @Param("timestamp") long timestamp);

    /**
     * 批量重置用户流量 u=0, d=0
     */
    @Update("<script>" +
            "UPDATE v2_user SET u = 0, d = 0, updated_at = #{updatedAt} " +
            "WHERE id IN " +
            "<foreach item='id' collection='userIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int resetTrafficByUserIds(@Param("userIds") List<Long> userIds, @Param("updatedAt") long updatedAt);
}

