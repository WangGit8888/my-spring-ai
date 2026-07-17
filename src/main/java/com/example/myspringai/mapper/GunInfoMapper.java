package com.example.myspringai.mapper;

import com.example.myspringai.domain.GunInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;


/**
* @author 19394
* @description 针对表【gun_info】的数据库操作Mapper
* @createDate 2026-03-03 16:41:17
* @Entity com.example.myspringai.domain.GunInfo
*/
@Mapper
public interface GunInfoMapper extends BaseMapper<GunInfo> {

}




