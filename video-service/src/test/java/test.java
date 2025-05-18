import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.hiiro.VideoServiceApplication;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.document.VideoDocument;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;

import java.util.List;
import java.util.Map;

@Slf4j
@SpringBootTest(classes = VideoServiceApplication.class)
public class test {

    @Resource
    RedisUtil redisUtil;
    @Resource
    UserFeignApi userFeignApi;
    @Resource
    ElasticsearchOperations esOperations;

    @Test
    void contextLoads() {
        List<CategoryDTO> object = redisUtil.getList("categoryList", 0, CategoryDTO.class);
        System.out.println(object);
    }

    @Test
    void contextLoads2() {
        System.out.println(userFeignApi.getBatchUserInfo(List.of(1L, 2L)));
    }

    // 创建ES索引
    @DisplayName("创建索引")
    @Test
    void createESIndex() {
        IndexOperations indexOps = esOperations.indexOps(VideoDocument.class);
        System.out.println(indexOps.exists());
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();
    }

    @Test
    @DisplayName("查看索引信息")
    void indexInfo() {
        //查看索引完整信息
        IndexOperations indexOperations = esOperations.indexOps(VideoDocument.class);
        List<IndexInformation> informations = indexOperations.getInformation();
        informations.forEach(indexInformation -> System.out.println(JSON.toJSONString(indexInformation, JSONWriter.Feature.PrettyFormat)));

        //查看索引映射信息
        Map<String, Object> mapping = indexOperations.getMapping();
        System.out.println("------------mapping----------------");
        System.out.println(JSON.toJSONString(mapping, JSONWriter.Feature.PrettyFormat));

        //查看索引设置信息
        Settings settings = indexOperations.getSettings(true);
        System.out.println("------------settings----------------");
        System.out.println(JSON.toJSONString(settings, JSONWriter.Feature.PrettyFormat));

    }

    @Test
    @DisplayName("删除索引")
    void deleteIndex() {
        IndexOperations indexOperations = esOperations.indexOps(VideoDocument.class);
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
    }

    @Test
    void test1() {
        Criteria title = new Criteria().contains("崩坏");
        Query query = new CriteriaQuery(title);
        SearchHits<VideoDocument> search = esOperations.search(query, VideoDocument.class);
        System.out.println(search.stream().map(SearchHit::getContent).toList());
    }

    @Test
    void test2() {
        String keyword = "mad";

        HighlightParameters highlightParams = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .build();

        List<HighlightField> highlightFields = List.of(
                new HighlightField("title"),
                new HighlightField("descr"),
                new HighlightField("tags")
        );

        Highlight highlight = new Highlight(highlightParams, highlightFields);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(keyword)
                        .fields("title", "descr", "tags")
                ))
                .withHighlightQuery(new HighlightQuery(highlight, VideoDocument.class))
                .build();

        SearchHits<VideoDocument> searchHits = esOperations.search(query, VideoDocument.class);

        // 打印内容
        List<VideoDocument> list = searchHits.stream()
                .map(SearchHit::getContent)
                .toList();
        System.out.println("查询内容结果：");
        System.out.println(list);

        // 打印高亮字段及文本
        System.out.println("\n查询高亮结果：");
        for (SearchHit<VideoDocument> hit : searchHits) {
            System.out.println("vid = " + hit.getContent().getVid());
            hit.getHighlightFields().forEach((field, fragments) -> {
                System.out.println("字段: " + field + " 高亮文本: " + String.join(", ", fragments));
            });
            System.out.println("----");
        }
    }


    @Test
    void test3() {
        NativeQuery query = NativeQuery.builder().withQuery(q -> q.matchAll(m -> m)).build();
        SearchHits<VideoDocument> searchHits = esOperations.search(query, VideoDocument.class);
        List<VideoDocument> list = searchHits.stream().map(SearchHit::getContent).toList();
        System.out.println(list);
    }
}
