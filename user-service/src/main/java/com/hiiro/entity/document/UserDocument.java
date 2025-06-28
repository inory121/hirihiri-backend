package com.hiiro.entity.document;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.*;

/**
 * 用户文档 - 用于Elasticsearch索引
 */
@Document(indexName = "user") // 索引名称
@Setting(settingPath = "/static/user-settings.json") // 自定义分析器配置
@Data
public class UserDocument {

    @Field(type = FieldType.Long)
    private Long uid; // 用户ID

    // 用户名 - 多字段配置（主字段+拼音字段+逐字字段）
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
            otherFields = {
                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyin_analyzer"),
                    @InnerField(suffix = "char", type = FieldType.Text, analyzer = "char_analyzer")
            }
    )
    private String username;

    // 昵称 - 多字段配置
//    @MultiField(
//            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
//            otherFields = {
//                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyin_analyzer"),
//                    @InnerField(suffix = "char", type = FieldType.Text, analyzer = "char_analyzer")
//            }
//    )
//    private String nickname;

    @Field(type = FieldType.Keyword, index = false)
    private String avatar; // 头像URL

//    @Field(type = FieldType.Keyword)
//    private String background; // 背景图URL
//
//    @Field(type = FieldType.Byte)
//    private Byte sex; // 性别 0私密 1男 2女

    // 个性签名 - 多字段配置
    @Field(type = FieldType.Keyword, index = false)
    private String description;

}