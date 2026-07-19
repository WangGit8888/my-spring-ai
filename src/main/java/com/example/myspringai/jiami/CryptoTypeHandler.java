package com.example.myspringai.jiami;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis-Plus 加解密 TypeHandler — 自动拦截 String 字段的读写，业务代码零侵入
 * <p>
 * 用法：在实体字段上加 {@code @TableField(typeHandler = CryptoTypeHandler.class)}
 */
@Slf4j
public class CryptoTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    String parameter, JdbcType jdbcType) throws SQLException {
        // 写入 DB 前加密
        ps.setString(i, SpringContextHolder.getBean(AesUtil.class).encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 从 DB 读出后解密
        return decryptIfNotNull(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decryptIfNotNull(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decryptIfNotNull(cs.getString(columnIndex));
    }

    private String decryptIfNotNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SpringContextHolder.getBean(AesUtil.class).decrypt(value);
        } catch (Exception e) {
            log.warn("解密失败，返回原始值", e);
            return value;
        }
    }
}
