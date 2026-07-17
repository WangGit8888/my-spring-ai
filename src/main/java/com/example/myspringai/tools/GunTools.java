package com.example.myspringai.tools;

import com.example.myspringai.domain.GunInfo;
import com.example.myspringai.domain.query.GunQuery;
import com.example.myspringai.service.GunInfoService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GunTools {
    private final GunInfoService gunInfoService;

    @Tool(description = "根据条件推荐枪械")
    public List<GunInfo> getGunInfo(@ToolParam(description = "枪械查询条件") GunQuery gunQuery) {
        List<GunInfo> gunByType = gunInfoService.getGunByType(gunQuery.getType());
        return gunByType;
    }

    @Tool(description = "根据信息存储枪械")
    public Integer saveGunInfo(@ToolParam(description = "枪械保存参数") GunQuery gunQuery) {

        GunInfo gunInfo = new GunInfo();
        gunInfo.setName(gunQuery.getName());
        gunInfo.setType(gunQuery.getType());
        gunInfo.setPower(gunQuery.getPower());
        gunInfo.setRateFire(gunQuery.getRateFire());
        gunInfoService.save(gunInfo);
        return gunInfo.getId();

    }
}
