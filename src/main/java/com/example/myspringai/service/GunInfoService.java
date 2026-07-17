package com.example.myspringai.service;

import com.example.myspringai.domain.GunInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 19394
* @description 针对表【gun_info】的数据库操作Service
* @createDate 2026-03-03 16:41:17
*/
public interface GunInfoService extends IService<GunInfo> {
List<GunInfo> getGunByType(String type);
}
