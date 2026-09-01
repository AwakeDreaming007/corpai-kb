package com.xufg.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL JSONB 与 Java String 的转换处理器，避免 JSON 字符串按 VARCHAR 写入失败。
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    /**
     * 将 JSON 字符串按 OTHER 类型发送，由 PostgreSQL 驱动映射到目标 JSONB 列。
     */
    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement,
                                    int parameterIndex,
                                    String parameter,
                                    JdbcType jdbcType) throws SQLException {
        preparedStatement.setObject(parameterIndex, parameter, Types.OTHER);
    }

    /**
     * 按列名读取 JSONB 文本。
     */
    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    /**
     * 按列序号读取 JSONB 文本。
     */
    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    /**
     * 按列序号读取存储过程返回的 JSONB 文本。
     */
    @Override
    public String getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        return callableStatement.getString(columnIndex);
    }
}
