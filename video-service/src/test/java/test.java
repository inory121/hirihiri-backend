import com.hiiro.VideoServiceApplication;
import com.hiiro.apis.UserFeignApi;
import com.hiiro.entity.dto.CategoryDTO;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@Slf4j
@SpringBootTest(classes = VideoServiceApplication.class)
public class test {

    @Resource
    RedisUtil redisUtil;
    @Resource
    UserFeignApi userFeignApi;
    @Test
    void contextLoads() {
        List<CategoryDTO> object = redisUtil.getList("categoryList",0,CategoryDTO.class);
        System.out.println(object);
    }
    @Test
    void contextLoads2() {
        System.out.println(userFeignApi.getBatchUserInfo(List.of(1L,2L)));
    }
}
