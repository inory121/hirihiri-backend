package com.hiiro.utils;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.model.ClassAnnotationAttributes;

import java.sql.Types;
import java.util.Collections;

public class MybatisplusUtil {
    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://127.0.0.1:3306/hirihiri?serverTimezone=Asia/Shanghai&useUnicode=true&" +
                                "characterEncoding=utf-8&useSSL=false&allowMultiQueries=true",
                        "root", "12345")
                .globalConfig(builder -> {
                    builder.author("hiiro") // 设置作者
                            .enableSwagger() // 开启 swagger 模式
                            .outputDir("E://"); // 指定输出目录
                })
                .dataSourceConfig(builder ->
                        builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                            int typeCode = metaInfo.getJdbcType().TYPE_CODE;
                            if (typeCode == Types.SMALLINT) {
                                // 自定义类型转换
                                return DbColumnType.INTEGER;
                            }
                            return typeRegistry.getColumnType(metaInfo);
                        })
                )
                .packageConfig(builder ->
                                builder.parent("com.hiiro") // 设置父包名
//                                .moduleName("system") // 设置父包模块名
                                        .pathInfo(Collections.singletonMap(OutputFile.xml, "E://")) // 设置mapperXml生成路径
                )
                .strategyConfig(builder ->
                                builder.addInclude("user") // 设置需要生成的表名
                                        .entityBuilder().enableLombok(new ClassAnnotationAttributes("@Data", "lombok.Data"))
                                        .mapperBuilder().enableFileOverride()
//                                .addTablePrefix("t_", "c_") // 设置过滤表前缀
                )
                .templateEngine(new FreemarkerTemplateEngine()) // 使用Freemarker引擎模板，默认的是Velocity引擎模板
                .execute();
    }
}
