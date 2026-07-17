package com.example.myspringai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.myspringai.domain.GunInfo;
import com.example.myspringai.service.GunInfoService;
import com.example.myspringai.mapper.GunInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 19394
* @description 针对表【gun_info】的数据库操作Service实现
* @createDate 2026-03-03 16:41:17
*/
@Service
public class GunInfoServiceImpl extends ServiceImpl<GunInfoMapper, GunInfo>
    implements GunInfoService{

    @Override
    public List<GunInfo> getGunByType(String type) {
        LambdaQueryWrapper<GunInfo> wrapper = new LambdaQueryWrapper<>();
        List<GunInfo> list = list(wrapper);
        return list;
    }
}




