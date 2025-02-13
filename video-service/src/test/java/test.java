import java.util.List;
import java.util.Locale.Category;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.hiiro.VideoServiceApplication;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.utils.RedisUtil;

import jakarta.annotation.Resource;

@SpringBootTest(classes = VideoServiceApplication.class)
public class test {

    @Resource
    RedisUtil redisUtil;
    
    @Test
    void contextLoads() {
        List<CategoryDTO> object = redisUtil.getList("categoryList",0,CategoryDTO.class);
        System.out.println(object);
    }
}
