package com.example.myspringai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.myspringai.domain.AlertInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预警记录 Mapper
 */
@Mapper
public interface AlertInfoMapper extends BaseMapper<AlertInfo> {
}
