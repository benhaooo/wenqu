package com.wenqu.jooq.binding;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;

/**
 * pgvector 的 {@code vector} 列与 {@code float[]} 之间的转换，由 jOOQ 代码生成引用（见 pom.xml forcedType）。与生成代码同在
 * {@code com.wenqu.jooq} 包下，生成的表类不依赖任何模块。
 *
 * <p>以文本形式 {@code [0.1,0.2,...]} 传输，参数显式转换为 vector，否则 PostgreSQL 不会把 varchar 参数隐式转换为 vector。
 */
public class PgVectorBinding implements Binding<Object, float[]> {

    private static final Converter<Object, float[]> CONVERTER = Converter.ofNullable(
            Object.class, float[].class, value -> parse(value.toString()), PgVectorBinding::format);

    @Override
    public Converter<Object, float[]> converter() {
        return CONVERTER;
    }

    @Override
    public void sql(BindingSQLContext<float[]> ctx) {
        if (ctx.render().paramType() == ParamType.INLINED) {
            ctx.render().visit(DSL.inline(format(ctx.value()))).sql("::public.vector");
        } else {
            ctx.render().sql(ctx.variable()).sql("::public.vector");
        }
    }

    @Override
    public void register(BindingRegisterContext<float[]> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
    }

    @Override
    public void set(BindingSetStatementContext<float[]> ctx) throws SQLException {
        ctx.statement().setString(ctx.index(), (String) ctx.convert(converter()).value());
    }

    @Override
    public void set(BindingSetSQLOutputContext<float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void get(BindingGetResultSetContext<float[]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.resultSet().getString(ctx.index()));
    }

    @Override
    public void get(BindingGetStatementContext<float[]> ctx) throws SQLException {
        ctx.convert(converter()).value(ctx.statement().getString(ctx.index()));
    }

    @Override
    public void get(BindingGetSQLInputContext<float[]> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    static String format(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder text = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }

    static float[] parse(String text) {
        String body = text.substring(1, text.length() - 1);
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
