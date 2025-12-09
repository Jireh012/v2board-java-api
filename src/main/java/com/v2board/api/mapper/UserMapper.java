package com.v2board.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.v2board.api.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

