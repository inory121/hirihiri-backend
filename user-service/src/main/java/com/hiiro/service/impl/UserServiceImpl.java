package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.document.UserDocument;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.UserDTOMapper;
import com.hiiro.mapper.UserMapper;
import com.hiiro.service.UserService;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author hiiro
 * @since 2025-01-29
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    MyJwtUtil jwtUtil;

    @Resource
    RedisUtil redisUtil;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserDTOMapper userDTOMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthenticationProvider authenticationProvider;

    @Resource
    ElasticsearchOperations esOperations;

    /**
     * 用户注册
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> register(User user) {
        // 验证用户名是否已存在
        if (Objects.nonNull(getUserByUsername(user.getUsername()))) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已存在!");
        }
        // 加密用户密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // 尝试注册用户
        if (userMapper.insert(user) == 1) {
            User dbUser = userMapper.selectById(user.getUid());
            // 保存用户信息到Elasticsearch
            esOperations.save(BeanUtil.copyProperties(dbUser, UserDocument.class));
            // 如果前端没有传nickname则使用默认格式,有则使用前端传来的nickname
            if (Objects.isNull(user.getNickname())) {
                // 设置用户昵称,格式为"hiri_{用户uid}"
                user.setNickname("hiri_" + user.getUid());
                // 更新用户昵称
                updateUserById(user);
            }
            return ResultData.success("注册成功，欢迎加入hirihiri！");
        } else {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户注册失败");
        }
    }

    /**
     * 用户登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Override
    public ResultData<HashMap<String, Object>> login(User user, Integer requiredRole) {
        // 创建一个UsernamePasswordAuthenticationToken对象，用于认证用户
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                user.getUsername(), user.getPassword());
        // 使用authenticationProvider对提供的用户名和密码进行验证
        Authentication authenticate = authenticationProvider.authenticate(authenticationToken);
        // 获取认证通过后的用户详细信息
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticate.getPrincipal();
        if (loginUser.getUser().getState() != 0) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "用户被封禁或已注销");
        }
        Byte userRole = loginUser.getUser().getRole();
        if (userRole == null) {
            return ResultData.fail(ResultCodeEnum.FORBIDDEN, "角色信息缺失");
        }
        if (requiredRole != null) {
            if (requiredRole == 1) { // 管理员登录
                if (userRole < 1) {
                    return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无管理员权限");
                }
            } else if (requiredRole == 0) { // 普通用户登录
                if (userRole != 0) {
                    return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无普通用户权限");
                }
            }
        }
        // 取出用户uid放入JWT令牌中
        Long uid = loginUser.getUser().getUid();
        // 创建默认的JWT令牌，其中包含用户的UID作为声明的一部分
        String token = jwtUtil.createDefaultJwtToken(new HashMap<>(Map.of("uid", uid)));
        // 登陆成功并成功更新登陆状态后将用户信息存入redis
        redisUtil.setWithDefaultExpire("user:" + uid, JSON.toJSONString(loginUser.getUser()));
        // 返回token和用户信息给前端
        return ResultData.success(
                new HashMap<>(
                        Map.of("user", getUserByUid(uid), "token", token)),
                "登陆成功!");

    }

    /**
     * 普通用户登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Override
    public ResultData<HashMap<String, Object>> userLogin(User user) {
        return login(user, 0);
    }

    /**
     * 管理员登录
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Override
    public ResultData<HashMap<String, Object>> adminLogin(User user) {
        return login(user, 1);
    }


    /**
     * 通过用户名获取用户信息
     *
     * @param username 用户名
     * @return user User实体
     */
    @Override
    public UserDTO getUserByUsername(String username) {
        return userDTOMapper.selectOne(new LambdaQueryWrapper<UserDTO>().eq(UserDTO::getUsername, username));
    }

    /**
     * 通过用户ID获取用户信息
     *
     * @param uid 用户ID
     * @return user User实体
     */
    @Override
    public UserDTO getUserByUid(Long uid) {
        return userDTOMapper.selectOne(new LambdaQueryWrapper<UserDTO>().eq(UserDTO::getUid, uid));
    }

    /**
     * 更新用户信息
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> updateUserById(User user) {
        if (userMapper.updateById(user) == 1) {
            return ResultData.success("更新用户信息成功");
        } else {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "更新用户信息失败");
        }
    }

    /**
     * 用户登出
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public ResultData<String> logout(String uid, String token) {
        if (!uid.isEmpty()) {
            // 从jwt中获取jti
            String jti = jwtUtil.getClaimFromToken(token, "jti");
            // 从redis中删除用户信息
            if (redisUtil.delete("user:" + uid, "token:user:" + uid)) {
                // 将token加入黑名单
                redisUtil.setWithDefaultExpire("blacklist:user:" + uid + ":" + jti, jti);
                return ResultData.success("用户登出成功");
            }
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已登出");
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在或用户未登录");
    }

    /**
     * 获取用户信息
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public ResultData<UserDTO> getUserInfo(String uid) {
        long startTime = System.currentTimeMillis();
        // 从redis中获取用户信息
        Optional<UserDTO> redisUserDTO = redisUtil.getObject("user:" + uid, UserDTO.class);
        if (redisUserDTO.isPresent()) {
            log.info("从缓存获取用户信息成功，耗时：{}ms", System.currentTimeMillis() - startTime);
            return ResultData.success(redisUserDTO.get(), "获取用户信息成功");
        } else {
            try {
                Long userId = Long.valueOf(uid);
                // 从数据库中获取用户信息
                UserDTO userDTO = getUserByUid(userId);
                if (Objects.nonNull(userDTO)) {
                    return ResultData.success(userDTO, "获取用户信息成功");
                } else {
                    return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("字符串无法转为 Long");
            }
        }
    }

    /**
     * 通过用户ID获取用户名
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public ResultData<UserDTO> getUserDTOByUid(Long uid) {
        UserDTO user = userDTOMapper.selectOne(new LambdaQueryWrapper<UserDTO>().eq(UserDTO::getUid, uid));
        if (Objects.nonNull(user)) {
            return ResultData.success(user);
        }
        return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
    }

    /**
     * 批量获取用户信息
     *
     * @param uids 用户ID列表
     * @return List<UserDTO>
     */
    @Override
    public List<UserDTO> getBatchUserInfo(List<Long> uids) {
        List<UserDTO> userList = userDTOMapper.selectByIds(uids);
        if (userList.isEmpty()) {
            return List.of();
        }
        return userList;
    }

    /**
     * 分页获取用户信息
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return ResultData对象
     */
    @Override
    public ResultData<List<UserDTO>> getUserPage(Integer pageNum, Integer pageSize) {
        // 设置默认分页参数
        if (pageNum == null || pageNum < 1) {
            pageNum = 1; // 最小页数1
        } else if (pageNum > 100) {
            pageNum = 100; // 最大页数100
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10; // 默认每页10条
        } else if (pageSize > 200) {
            pageSize = 200; // 最大每页200条
        }
        Page<UserDTO> page = new Page<>(pageNum, pageSize);
        IPage<UserDTO> userPage = new LambdaQueryChainWrapper<>(userDTOMapper).page(page);
        List<UserDTO> userList = userPage.getRecords();
        if (userList.isEmpty()) {
            return ResultData.success(Collections.emptyList(), "无用户信息");
        }
        return ResultData.success(userList);
    }

    /**
     * 搜索用户
     *
     * @param keyword  关键词
     * @param pageNum  分页页数
     * @param pageSize 分页大小
     * @return ResultData对象
     */
    @Override
    public ResultData<List<UserDocument>> searchUsers(String keyword, Integer pageNum, Integer pageSize) {
        // 1. 构建 NativeQuery，多字段匹配
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.wildcard(w -> w  // 通配符查询
                                .field("username")
                                .value("*" + keyword + "*")
                                .caseInsensitive(true)
                                .boost(5.0f)))
                ))
                .withSort(s -> s.field(f -> f
                        .field("_score")
                        .order(SortOrder.Desc)
                ))
                .build();

        // 2. 执行搜索
        SearchHits<UserDocument> search = esOperations.search(query, UserDocument.class);
        if (search.getTotalHits() == 0) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST);
        }
        search.getSearchHits().forEach(hit -> {
            System.out.println("用户" + hit.getContent().getUid() + " 得分：" + hit.getScore());
        });
        // 3. 按ES顺序收集结果（LinkedHashMap保持顺序）
//        LinkedHashMap<Long, SearchHit<VideoDocument>> orderedHits = new LinkedHashMap<>();
//        Map<Long, String> titleHighlightMap = new HashMap<>(); // 高亮存储
//
//        search.get().forEach(hit -> {
//            Long vid = hit.getContent().getVid();
//            orderedHits.put(vid, hit);
//            // 处理高亮
//            if (hit.getHighlightFields().containsKey("title")) {
//                List<String> highlights = hit.getHighlightFields().get("title");
//                if (!highlights.isEmpty()) {
//                    titleHighlightMap.put(vid, highlights.get(0));
//                }
//            }
//        });

        // 4. 按ES顺序查询数据库
//        List<Video> videos = new LambdaQueryChainWrapper<>(videoMapper)
//                .in(Video::getVid, new ArrayList<>(orderedHits.keySet()))
//                .list();

        // 5. 按ES顺序重组结果
//        List<Video> orderedVideos = new ArrayList<>();
//        orderedHits.forEach((vid, hit) ->
//                videos.stream()
//                        .filter(v -> v.getVid().equals(vid))
//                        .findFirst()
//                        .ifPresent(video -> {
//                            // 应用高亮标题
//                            if (titleHighlightMap.containsKey(vid)) {
//                                video.setTitle(titleHighlightMap.get(vid));
//                            }
//                            orderedVideos.add(video);
//                        })
//        );

        // 6. 分页处理
//        Page<Video> page = validateAndBuildPage(pageNum, pageSize);
//        page.setRecords(orderedVideos);
//        return processVideoPage(page);
        List<UserDocument> userDocumentList = search.stream()
                .map(SearchHit::getContent)
                .toList();
        return ResultData.success(userDocumentList);
    }

}
