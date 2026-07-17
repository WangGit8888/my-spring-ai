package com.example.myspringai.domain.query;

import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
public class GunQuery {
    /**
     *
     */
    @TableId
    private Long id;

    /**
     *
     */
    @ToolParam(required = false,description = "枪械名称")
    private String name;

    /**
     *
     */
    @ToolParam(required = false,description = "枪械类型:自动步枪、冲锋枪、狙击枪")
    private String type;

    /**
     *
     */
    @ToolParam(required = false,description = "枪械威力,值越威力越大")
    private String power;

    /**
     *
     */
    @ToolParam(required = false,description = "枪械射速,值越大射速越快")
    private String rateFire;
}
