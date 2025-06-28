package com.hiiro.entity.document;


import lombok.Data;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "video")// 自动创建索引时需要自定义分析器配置
@Setting(settingPath = "/static/video-settings.json")// 自定义分析器设置
@Data
public class VideoDocument {

    @Field(type = FieldType.Long)
    private Long vid;

    @Field(type = FieldType.Long)
    private Long uid;

    @MultiField(mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
            otherFields = {
                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyin_analyzer"),
                    @InnerField(suffix = "char", type = FieldType.Text, analyzer = "char_analyzer")
            })// 多字段配置：主字段+拼音字段+逐字字段
    private String title;

    @Field(type = FieldType.Keyword)
    private String mcId;

    @Field(type = FieldType.Keyword)
    private String scId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word"),
            otherFields = {
                    @InnerField(suffix = "pinyin", type = FieldType.Text, analyzer = "pinyin_analyzer"),
                    @InnerField(suffix = "char", type = FieldType.Text, analyzer = "char_analyzer")
            }
    )
    private String descr;

    @Field(
            type = FieldType.Text,
            analyzer = "tag_analyzer",  // 自定义标签分析器
            fielddata = true
    )
    private String tags;

}