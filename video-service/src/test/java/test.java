import com.hiiro.VideoServiceApplication;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

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
