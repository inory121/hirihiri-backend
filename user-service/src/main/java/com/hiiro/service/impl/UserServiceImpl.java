package com.hiiro.service.impl;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hiiro.entity.Follow;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.ResultData;
import com.hiiro.entity.User;
import com.hiiro.entity.UserExpDaily;
import com.hiiro.entity.UserDailyCoin;
import com.hiiro.entity.document.UserDocument;
import com.hiiro.entity.dto.RegisterDTO;
import com.hiiro.entity.dto.UserDTO;
import com.hiiro.mapper.FollowMapper;
import com.hiiro.mapper.UserExpDailyMapper;
import com.hiiro.mapper.UserDTOMapper;
import com.hiiro.mapper.UserDailyCoinMapper;
import com.hiiro.mapper.UserMapper;
import com.hiiro.service.UserService;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private UserDailyCoinMapper userDailyCoinMapper;

    @Resource
    private UserExpDailyMapper userExpDailyMapper;

    @Resource
    private FollowMapper followMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private AuthenticationProvider authenticationProvider;

    @Resource
    ElasticsearchOperations esOperations;

    /**
     * 用户注册
     *
     * @param dto RegisterDTO
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> register(RegisterDTO dto) {
        // 验证用户名是否已存在（快速失败检查）
        if (Objects.nonNull(getUserByUsername(dto.getUsername()))) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已存在!");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        // 加密用户密码
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname());

        try {
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
        } catch (DuplicateKeyException e) {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "用户已存在!");
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
        // Lv1+ 用户每日登录自动发放 +1 硬币（与经验解耦，互不影响）
        grantDailyLoginCoin(uid);
        // 登录经验奖励：每天一次固定 5 点，与等级/是否领币无关，由 addExp 按 login 类型幂等保证
        try {
            addExp(uid, "login", 5);
        } catch (Exception e) {
            log.warn("登录经验发放失败: {}", e.getMessage());
        }
        // 登陆成功后将用户DTO（不含密码等敏感信息）存入redis
        UserDTO currentUser = getUserByUid(uid);
        redisUtil.setWithDefaultExpire("user:" + uid, JSON.toJSONString(currentUser));
        // 返回token和用户信息给前端
        return ResultData.success(
                new HashMap<>(
                        Map.of("user", currentUser, "token", token)),
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
     * 通过用户ID获取用户信息（统一走 Redis 缓存）
     *
     * @param uid 用户ID
     * @return user UserDTO对象，查不到返回 null
     */
    @Override
    public UserDTO getUserByUid(Long uid) {
        if (uid == null) {
            return null;
        }
        // 1. 先查缓存
        Optional<UserDTO> redisUser = redisUtil.getObject("user:" + uid, UserDTO.class);
        if (redisUser.isPresent()) {
            return redisUser.get();
        }
        // 2. 缓存未命中 → 查数据库
        UserDTO userDTO = userDTOMapper.selectOne(new LambdaQueryWrapper<UserDTO>().eq(UserDTO::getUid, uid));
        // 3. 写回缓存（有值才写）
        if (userDTO != null) {
            redisUtil.setWithDefaultExpire("user:" + uid, JSON.toJSONString(userDTO));
        }
        return userDTO;
    }

    /**
     * 更新用户信息（字段白名单控制）
     *
     * @param user User实体
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> updateUserById(User user) {
        if (user.getUid() == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "用户ID不能为空");
        }

        // 从Security上下文获取当前登录用户
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean hasLoginUser = auth != null && auth.getPrincipal() instanceof User;

        User updateUser = new User();
        updateUser.setUid(user.getUid());

        if (!hasLoginUser) {
            // 内部服务调用：拷贝除 uid、password 外的所有字段
            BeanUtil.copyProperties(user, updateUser, "uid", "password");
        } else {
            User currentUser = (User) auth.getPrincipal();
            Byte role = currentUser.getRole();

            // 非超级管理员必须只能改自己的信息
            if (role == null || role != 2) {
                if (!user.getUid().equals(currentUser.getUid())) {
                    return ResultData.fail(ResultCodeEnum.FORBIDDEN, "无权限修改其他用户信息");
                }
            }

            if (role != null && role == 2) {
                // 超级管理员：允许改公开信息 + 管理字段（但禁止改 uid / role / password）
                BeanUtil.copyProperties(user, updateUser, "uid", "password", "role");
            } else {
                // 普通用户/管理员：只能改自己的公开信息
                BeanUtil.copyProperties(user, updateUser,
                        "uid", "password", "role", "state", "auth", "authMsg", "exp", "coin", "vip");
            }
        }

        if (userMapper.updateById(updateUser) == 1) {
            // 同步到ES
            User dbUser = userMapper.selectById(user.getUid());
            if (dbUser != null) {
                esOperations.save(BeanUtil.copyProperties(dbUser, UserDocument.class));
            }
            return ResultData.success("更新用户信息成功");
        } else {
            return ResultData.fail(ResultCodeEnum.INTERNAL_SERVER_ERROR, "更新用户信息失败");
        }
    }

    /**
     * 用户登出（校验token与uid对应关系）
     *
     * @param uid   用户ID
     * @param token token
     * @return ResultData对象
     */
    @Override
    public ResultData<String> logout(String uid, String token) {
        if (uid == null || uid.isEmpty() || token == null || token.isEmpty()) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数不能为空");
        }

        // 校验token有效性
        if (!jwtUtil.verifyJwtToken(token)) {
            return ResultData.fail(ResultCodeEnum.UNAUTHORIZED, "token无效");
        }

        // 校验token中uid与请求uid匹配
        String tokenUid = jwtUtil.getClaimFromToken(token, "uid");
        if (!uid.equals(tokenUid)) {
            return ResultData.fail(ResultCodeEnum.FORBIDDEN, "token与用户不匹配");
        }

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

    /**
     * 获取用户信息
     *
     * @param uid 用户ID
     * @return ResultData对象
     */
    @Override
    public ResultData<UserDTO> getUserInfo(String uid) {
        try {
            UserDTO userDTO = getUserByUid(Long.valueOf(uid));
            if (Objects.nonNull(userDTO)) {
                return ResultData.success(userDTO, "获取用户信息成功");
            }
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("uid 格式错误");
        }
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
    public ResultData<Map<String, Object>> searchUsers(String keyword, Integer pageNum, Integer pageSize, String order, Long currentUid) {
        if (pageNum == null || pageNum < 1) pageNum = 1;
        else if (pageNum > 100) pageNum = 100;
        if (pageSize == null || pageSize < 1) pageSize = 10;
        else if (pageSize > 50) pageSize = 50;
        if (order == null || order.isEmpty()) order = "default";

        int fetchSize = 200;
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .should(s -> s.wildcard(w -> w
                                .field("username")
                                .value("*" + keyword + "*")
                                .caseInsensitive(true)
                                .boost(5.0f)))
                ))
                .withSort(s -> s.field(f -> f
                        .field("_score")
                        .order(SortOrder.Desc)
                ))
                .withPageable(PageRequest.of(0, fetchSize))
                .build();

        SearchHits<UserDocument> search = esOperations.search(query, UserDocument.class);
        if (search.getTotalHits() == 0) {
            Map<String, Object> empty = new HashMap<>(2);
            empty.put("records", Collections.emptyList());
            empty.put("total", 0);
            return ResultData.success(empty);
        }

        List<Long> uidList = search.stream()
                .map(hit -> hit.getContent().getUid())
                .toList();

        List<User> users = new LambdaQueryChainWrapper<>(userMapper)
                .in(User::getUid, uidList)
                .list();

        Map<Long, User> userMap = new HashMap<>();
        for (User u : users) {
            userMap.put(u.getUid(), u);
        }

        List<User> orderedUsers = new ArrayList<>();
        for (Long uid : uidList) {
            User u = userMap.get(uid);
            if (u != null) {
                orderedUsers.add(u);
            }
        }

        Map<Long, Long> fanCountMap = new HashMap<>();
        Map<Long, Long> videoCountMap = new HashMap<>();
        if (!orderedUsers.isEmpty()) {
            List<Long> orderedUids = orderedUsers.stream().map(User::getUid).toList();
            List<Map<String, Object>> fanCounts = userMapper.getFanCountBatch(orderedUids);
            for (Map<String, Object> m : fanCounts) {
                fanCountMap.put(((Number) m.get("uid")).longValue(), ((Number) m.get("fan_count")).longValue());
            }
            List<Map<String, Object>> videoCounts = userMapper.getVideoCountBatch(orderedUids);
            for (Map<String, Object> m : videoCounts) {
                videoCountMap.put(((Number) m.get("uid")).longValue(), ((Number) m.get("video_count")).longValue());
            }
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (User u : orderedUsers) {
            Map<String, Object> map = new HashMap<>();
            map.put("uid", u.getUid());
            map.put("username", u.getUsername());
            map.put("nickname", u.getNickname());
            map.put("avatar", u.getAvatar());
            map.put("description", u.getDescription());
            map.put("exp", u.getExp());
            map.put("level", calcLevel(u.getExp()));
            map.put("auth", u.getAuth());
            map.put("authMsg", u.getAuthMsg());
            map.put("vip", u.getVip());
            map.put("fanCount", fanCountMap.getOrDefault(u.getUid(), 0L));
            map.put("videoCount", videoCountMap.getOrDefault(u.getUid(), 0L));
            resultList.add(map);
        }

        switch (order) {
            case "fan_desc" -> resultList.sort((a, b) ->
                    Long.compare((Long) b.get("fanCount"), (Long) a.get("fanCount")));
            case "fan_asc" -> resultList.sort((a, b) ->
                    Long.compare((Long) a.get("fanCount"), (Long) b.get("fanCount")));
            case "level_desc" -> resultList.sort((a, b) ->
                    Integer.compare((Integer) b.get("level"), (Integer) a.get("level")));
            case "level_asc" -> resultList.sort((a, b) ->
                    Integer.compare((Integer) a.get("level"), (Integer) b.get("level")));
            default -> {
            }
        }

        // 填充 isFollowing 字段
        if (currentUid != null && !resultList.isEmpty()) {
            List<Long> targetUids = resultList.stream()
                    .map(m -> ((Number) m.get("uid")).longValue())
                    .filter(uid -> !uid.equals(currentUid))
                    .toList();
            if (!targetUids.isEmpty()) {
                List<Long> myFollowingUids = new LambdaQueryChainWrapper<>(followMapper)
                        .select(Follow::getFollowingUid)
                        .eq(Follow::getFollowerUid, currentUid)
                        .in(Follow::getFollowingUid, targetUids)
                        .list()
                        .stream()
                        .map(Follow::getFollowingUid)
                        .toList();
                Set<Long> followingSet = new HashSet<>(myFollowingUids);
                for (Map<String, Object> map : resultList) {
                    Long uid = ((Number) map.get("uid")).longValue();
                    map.put("isFollowing", followingSet.contains(uid));
                }
            } else {
                for (Map<String, Object> map : resultList) {
                    map.put("isFollowing", false);
                }
            }
        } else {
            for (Map<String, Object> map : resultList) {
                map.put("isFollowing", false);
            }
        }

        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, resultList.size());
        Map<String, Object> body = new HashMap<>(2);
        if (fromIndex >= resultList.size()) {
            body.put("records", Collections.emptyList());
            body.put("total", resultList.size());
            return ResultData.success(body, "用户列表为空");
        }
        List<Map<String, Object>> pageResult = resultList.subList(fromIndex, toIndex);
        body.put("records", pageResult);
        body.put("total", resultList.size());
        return ResultData.success(body);
    }

    private int calcLevel(Integer exp) {
        if (exp == null) return 0;
        int[] levelExp = {0, 200, 1500, 4500, 10800, 28800};
        for (int i = levelExp.length - 1; i >= 0; i--) {
            if (exp >= levelExp[i]) {
                return i + 1;
            }
        }
        return 1;
    }

    /**
     * 增加/减少用户硬币
     *
     * @param uid    用户id
     * @param amount 变化数量（正数增加，负数减少）
     * @return ResultData对象
     */
    @Transactional
    @Override
    public ResultData<String> addCoin(Long uid, Double amount) {
        if (uid == null || amount == null) {
            return ResultData.fail(ResultCodeEnum.BAD_REQUEST, "参数错误");
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            return ResultData.fail(ResultCodeEnum.USER_NOT_EXIST, "用户不存在");
        }
        double currentCoin = user.getCoin() == null ? 0.0 : user.getCoin();
        user.setCoin(currentCoin + amount);
        userMapper.updateById(user);
        syncUserToEsAndEvictCache(user);
        return ResultData.success("硬币更新成功");
    }

    /**
     * 每日登录自动发放 +1 硬币（Lv1+ 每天限一次）
     *
     * @param uid 用户id
     */
    @Transactional
    public void grantDailyLoginCoin(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            return;
        }
        // Lv0（经验值 < 200）不发放
        Integer exp = user.getExp();
        if (exp == null || exp < 200) {
            return;
        }
        LocalDate today = LocalDate.now();
        UserDailyCoin existing = userDailyCoinMapper.selectOne(
                Wrappers.<UserDailyCoin>lambdaQuery()
                        .eq(UserDailyCoin::getUid, uid)
                        .eq(UserDailyCoin::getDate, today)
        );
        if (existing != null) {
            return;
        }
        UserDailyCoin daily = new UserDailyCoin();
        daily.setUid(uid);
        daily.setDate(today);
        daily.setGranted(1);
        daily.setCreateTime(LocalDateTime.now());
        userDailyCoinMapper.insert(daily);

        double currentCoin = user.getCoin() == null ? 0.0 : user.getCoin();
        user.setCoin(currentCoin + 1.0);
        userMapper.updateById(user);
        syncUserToEsAndEvictCache(user);
    }

    /**
     * 增加经验值（按来源类型每日幂等，每天每类只发一次）
     *
     * @param uid    用户id
     * @param type   经验来源类型：login / watch / vip_watch / share / coin
     * @param amount 本次发放经验值
     * @return 实际增加的经验值（当天该类型已发过则返回 0）
     */
    @Transactional
    @Override
    public ResultData<Integer> addExp(Long uid, String type, Integer amount) {
        if (uid == null || type == null || type.isEmpty() || amount == null || amount <= 0) {
            return ResultData.success(0);
        }
        LocalDate today = LocalDate.now();
        UserExpDaily existing = userExpDailyMapper.selectOne(
                Wrappers.<UserExpDaily>lambdaQuery()
                        .eq(UserExpDaily::getUid, uid)
                        .eq(UserExpDaily::getDate, today)
                        .eq(UserExpDaily::getExpType, type)
        );
        // 当天该来源已发放过，直接返回 0（每日只加一次）
        if (existing != null) {
            return ResultData.success(0);
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            return ResultData.success(0);
        }
        int exp = user.getExp() == null ? 0 : user.getExp();
        user.setExp(exp + amount);
        userMapper.updateById(user);
        syncUserToEsAndEvictCache(user);

        UserExpDaily daily = new UserExpDaily();
        daily.setUid(uid);
        daily.setDate(today);
        daily.setExpType(type);
        daily.setExpGain(amount);
        daily.setCreateTime(LocalDateTime.now());
        userExpDailyMapper.insert(daily);
        return ResultData.success(amount);
    }

    /**
     * 同步用户到 ES 并清理 Redis 缓存
     *
     * @param user User实体
     */
    private void syncUserToEsAndEvictCache(User user) {
        UserDocument userDocument = new UserDocument();
        BeanUtil.copyProperties(user, userDocument);
        esOperations.save(userDocument);
        redisUtil.delete("user:" + user.getUid());
    }

    /**
     * 增加投币经验值（每日上限50）
     *
     * @param uid           用户id
     * @param requestedGain 请求增加的经验值
     * @return 实际增加的经验值
     */
    @Transactional
    @Override
    public ResultData<Integer> addCoinExp(Long uid, Integer requestedGain) {
        if (uid == null || requestedGain == null || requestedGain <= 0) {
            return ResultData.success(0);
        }
        LocalDate today = LocalDate.now();
        UserExpDaily daily = userExpDailyMapper.selectOne(
                Wrappers.<UserExpDaily>lambdaQuery()
                        .eq(UserExpDaily::getUid, uid)
                        .eq(UserExpDaily::getDate, today)
                        .eq(UserExpDaily::getExpType, "coin")
        );
        int used = daily == null ? 0 : (daily.getExpGain() == null ? 0 : daily.getExpGain());
        int cap = 50;
        int gain = Math.min(requestedGain, Math.max(0, cap - used));
        if (gain > 0) {
            User user = userMapper.selectById(uid);
            if (user != null) {
                int exp = user.getExp() == null ? 0 : user.getExp();
                user.setExp(exp + gain);
                userMapper.updateById(user);
                syncUserToEsAndEvictCache(user);
            }
            if (daily == null) {
                daily = new UserExpDaily();
                daily.setUid(uid);
                daily.setDate(today);
                daily.setExpType("coin");
                daily.setExpGain(gain);
                daily.setCreateTime(LocalDateTime.now());
                daily.setUpdateTime(LocalDateTime.now());
                userExpDailyMapper.insert(daily);
            } else {
                daily.setExpGain(used + gain);
                daily.setUpdateTime(LocalDateTime.now());
                userExpDailyMapper.updateById(daily);
            }
        }
        return ResultData.success(gain);
    }

}
