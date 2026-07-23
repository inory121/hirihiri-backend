"""
hirihiri 全表测试数据生成脚本
按依赖顺序生成：user(大量) → danmaku → follow
→ favorite_folder(默认收藏夹) → browse_history → like/coin/collect → 同步 video_stat → 同步用户到ES
注意：category 和 video 表不动
依赖：pymysql, bcrypt, elasticsearch
"""
import pymysql
import random
import hashlib
import bcrypt
from datetime import datetime, timedelta
from elasticsearch import Elasticsearch
from elasticsearch.helpers import bulk

# ====================== 配置 ======================
DB_CONFIG = dict(host='localhost', user='root', password='12345',
                 database='hirihiri', charset='utf8mb4')
BATCH_SIZE = 500
TARGET_USER_COUNT = 1000  # 目标用户总数

# ====================== ES 配置 ======================
ES_CONFIG = {'hosts': ['http://localhost:9200']}
ES_USER_INDEX = 'user'

# ====================== 用户名/昵称素材 ======================
# 中文姓
SURNAMES = list('赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜戚谢邹喻柏窦苏潘葛范彭鲁韦昌马苗凤花方俞任袁柳鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮齐康伍余元卜顾孟平黄')
# 中文名
NAME_CHARS = list('伟刚勇毅俊峰强军平保东文辉力明永健世广志义兴良海山仁波宁贵福生龙元全国胜学祥才发武新利清飞彬富顺信子杰涛昌成康星光天达安岩中茂进林有坚和彪博诚先敬震振壮会思群豪心邦承乐绍功松善厚庆磊民友裕河哲江超浩亮政谦亨奇固之轮翰朗伯宏言若鸣朋斌梁栋维克翔旭鹏泽晨辰士以建家致树炎德行时泰盛秀娟英华慧巧美静淑惠珠翠雅芝玉萍红娥玲芬芳燕彩春菊兰凤洁梅琳素云莲真环雪荣妹霞香月莺媛艳瑞凡佳嘉琼勤珍贞莉桂娣叶璧璐娅琦晶妍茜秋珊莎锦黛青倩婷姣婉娴瑾颖露瑶怡婵雁蓓纨仪荷丹蓉眉君琴蕊薇菁梦岚苑婕馨瑗琰韵融园艺咏卿聪澜纯毓悦昭冰爽琬茗羽希宁飘')
# 英文/数字风格用户名前缀
EN_PREFIXES = ['xiao', 'da', 'lao', 'a', 'x', 'i', 'mini', 'super', 'pro', 'neo',
               'dark', 'light', 'ice', 'fire', 'sky', 'moon', 'star', 'sun', 'cloud',
               'wind', 'rain', 'snow', 'cat', 'dog', 'fox', 'bear', 'wolf', 'lion',
               'tiger', 'eagle', 'hawk', 'bird', 'fish', 'whale', 'shark', 'panda',
               'koala', 'rabbit', 'deer', 'horse', 'sheep', 'cow', 'pig', 'duck',
               'happy', 'sad', 'cool', 'hot', 'fast', 'slow', 'big', 'small',
               'crazy', 'lazy', 'busy', 'shy', 'brave', 'calm', 'wild', 'gentle',
               'pixel', 'cyber', 'retro', 'neo', 'meta', 'ultra', 'mega', 'hyper']
EN_SUFFIXES = ['er', 'or', 'ist', 'ism', 'y', 'ly', 'zy', 'ky', 'ny', 'fy',
               '123', '666', '888', '999', '007', '404', '233', '42', '99', '00',
               '_chan', '_kun', '_san', '_sama', 'x', 'xx', 'zz', 'qq', 'yy']
# 昵称模板
NICK_TEMPLATES = [
    '{surname}{name}', '{surname}{name}{name}',
    '{surname}{adj}{noun}', '快乐的{noun}', '爱{action}的{noun}',
    '{noun}爱好者', '{adj}的{noun}', '{noun}不吃{food}',
    '{action}中...', '今天{action}了吗', '{noun}{number}',
    '{surname}同学', '{surname}老师', '小{noun}', '大{noun}',
    '{noun}酱', '{noun}君', '{noun}大王', '{adj}{noun}',
]
NICK_ADJ = ['快乐', '忧郁', '暴躁', '温柔', '可爱', '高冷', '沙雕', '佛系', '摸鱼', '努力']
NICK_NOUN = ['猫咪', '柴犬', '企鹅', '水母', '仓鼠', '熊猫', '考拉', '水獭', '海豹', '鹦鹉',
             '咸鱼', '薯条', '西瓜', '草莓', '芒果', '豆腐', '奶茶', '可乐', '火锅', '拉面',
             '码农', '学生', '打工人', '干饭人', '社畜', 'UP主', '观众', '路人']
NICK_ACTION = ['摸鱼', '划水', '干饭', '睡觉', '打游戏', '看番', '追剧', '学习', '加班', '发呆']
NICK_FOOD = ['香菜', '苦瓜', '榴莲', '姜', '蒜', '辣椒', '芥末', '芹菜', '洋葱', '胡萝卜']

# 个性签名
DESCRIPTIONS = [
    '这个人很懒，什么都没留下~',
    '生活不止眼前的代码，还有诗和远方',
    '一个普通的{noun}爱好者',
    '每天进步一点点',
    '今天也要元气满满哦！',
    '佛系{action}中...',
    '在{place}看{thing}',
    '梦想是{dream}',
    'nothing to say',
    '这个人神秘兮兮的，什么都没写',
    '正在努力成为想成为的人',
    '保持热爱，奔赴山海',
    '先成为自己的英雄',
    '人生如戏，全靠演技',
    '间歇性踌躇满志，持续性混吃等死',
    '好看的皮囊千篇一律，有趣的灵魂万里挑一',
    '不定义自己',
    '活着就是为了改变世界',
    '一个{adj}的人',
    '正在输入中...',
]
DESC_PLACE = ['北京', '上海', '广州', '深圳', '杭州', '成都', '武汉', '南京', '重庆', '西安',
              '东京', '大阪', '巴黎', '伦敦', '纽约']
DESC_THING = ['日落', '星空', '大海', '雪山', '樱花', '极光', '彩虹', '月亮']
DESC_DREAM = ['环游世界', '成为UP主', '做一款游戏', '写一本书', '养十只猫', '开一家咖啡店']
DESC_ADJ = ['有趣', '无聊', '普通', '特别', '奇怪', '平凡', '矛盾']

# 弹幕内容
DANMAKU_POOL = (
    ['哈哈哈哈哈'] * 8 + ['awsl', '泪目', '太强了', '好家伙', '名场面', '笑死',
     '来了来了', '爷青回', '666666', 'tql', '前方高能', '前方核能', '经典',
     '绝了', '卧槽', '牛逼', 'DNA动了', '我哭了', '妙啊', '好耶',
     '下次一定', '三连了', '催更', '打卡', '第一', 'mark', '考古',
     '啊啊啊啊', '呜呜呜', '冲冲冲', '干杯~', '绝绝子', '真不戳',
     '等等这是什么', '没看懂', '求解释', '这段看了三遍', '暂停截图了',
     '老婆！！！', '老公好帅', '这对CP我磕了', '伏笔回收', '弹幕护体',
     '空耳好评', '全体起立', '教练我想学这个'] * 3
    + ['这段BGM绝了', 'OP好好听', 'ED别走', '剧情反转了', '有被帅到',
       '倒回去再看一遍', '弹幕大军来袭', '稳住我们能赢', '是我先来的',
       '你指尖跃动的电光', 'Production!', '老番新看', '2024年来报到',
       '好看好看好看', '整活', '好活', '太强了叭', '我直接好家伙']
)
DM_FONTSIZES = [18] + [25] * 5 + [32]
DM_COLORS = ['#FFFFFF'] * 10 + ['#FE0302', '#FF7204', '#FFAA02', '#FFD302',
           '#FFFF00', '#A0EE00', '#00CD00', '#019899', '#4266BE',
           '#89D5FF', '#CC0273']



# ====================== 工具函数 ======================
def rand_date(start_days=365, end_days=0):
    return datetime.now() - timedelta(
        days=random.randint(end_days, start_days),
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59),
        seconds=random.randint(0, 59))

def gen_username(idx):
    """生成用户名：中英混合风格"""
    style = random.random()
    if style < 0.4:
        # 英文前缀+数字
        return random.choice(EN_PREFIXES) + random.choice(EN_SUFFIXES) + f'_{idx}'
    elif style < 0.7:
        # 拼音风格: 随机字母+数字
        py = ''.join(random.choices('abcdefghijklmnopqrstuvwxyz', k=random.randint(3, 6)))
        return f'{py}{idx}'
    else:
        # user_idx_style 风格
        return f'user_{idx}_{random.choice(EN_PREFIXES)}'

def gen_nickname():
    """生成中文昵称"""
    tpl = random.choice(NICK_TEMPLATES)
    result = tpl
    replacements = {
        '{surname}': random.choice(SURNAMES),
        '{name}': random.choice(NAME_CHARS),
        '{adj}': random.choice(NICK_ADJ),
        '{noun}': random.choice(NICK_NOUN),
        '{action}': random.choice(NICK_ACTION),
        '{food}': random.choice(NICK_FOOD),
        '{number}': str(random.randint(1, 999)),
    }
    for k, v in replacements.items():
        result = result.replace(k, v)
    return result

def gen_description():
    tpl = random.choice(DESCRIPTIONS)
    replacements = {
        '{noun}': random.choice(NICK_NOUN),
        '{action}': random.choice(NICK_ACTION),
        '{place}': random.choice(DESC_PLACE),
        '{thing}': random.choice(DESC_THING),
        '{dream}': random.choice(DESC_DREAM),
        '{adj}': random.choice(DESC_ADJ),
    }
    for k, v in replacements.items():
        tpl = tpl.replace(k, v)
    return tpl

def batch_insert(cursor, conn, sql, data, desc=''):
    total = 0
    for i in range(0, len(data), BATCH_SIZE):
        chunk = data[i:i + BATCH_SIZE]
        cursor.executemany(sql, chunk)
        conn.commit()
        total += len(chunk)
    if desc:
        print(f'  ✅ {desc}: {total} 条')
    return total


# ====================== 主流程 ======================
def main():
    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()
    print('=' * 50)
    print('hirihiri 全表测试数据生成')
    print('=' * 50)

    # ==================== 1. user (大量) ====================
    print(f'\n👤 [1/9] 用户表 user (目标: {TARGET_USER_COUNT})')
    cursor.execute("SELECT COUNT(*) FROM user")
    existing_user_count = cursor.fetchone()[0]
    cursor.execute("SELECT MAX(uid) FROM user")
    max_uid = cursor.fetchone()[0] or 0
    cursor.execute("SELECT username FROM user")
    existing_usernames = set(r[0] for r in cursor.fetchall())

    need_users = max(0, TARGET_USER_COUNT - existing_user_count)
    if need_users > 0:
        # 预生成所有用户名确保不重复
        usernames_generated = set()
        user_rows = []
        uid_counter = max_uid
        attempts = 0
        pwd_hash = bcrypt.hashpw(
            hashlib.sha256(('123456' + 'hiri_frontend_salt').encode()).hexdigest().encode(),
            bcrypt.gensalt()
        ).decode()
        default_avatar = 'https://hirihiri.oss-cn-nanjing.aliyuncs.com/noface.jpg'
        default_bg = 'https://hirihiri.oss-cn-nanjing.aliyuncs.com/background.png'

        while len(user_rows) < need_users and attempts < need_users * 3:
            attempts += 1
            uid_counter += 1
            uname = gen_username(uid_counter)
            # 确保不重复
            if uname in existing_usernames or uname in usernames_generated:
                # 加后缀避免重复
                uname = f'{uname}_{uid_counter}'
            usernames_generated.add(uname)

            nickname = gen_nickname()
            sex = random.choices([0, 1, 2], weights=[15, 45, 40])[0]
            desc = gen_description()
            exp = int(random.expovariate(1 / 2000))
            coin = int(random.expovariate(1 / 500))
            vip = random.choices([0, 1, 2], weights=[80, 12, 8])[0]
            auth = random.choices([0, 1, 2], weights=[92, 5, 3])[0]
            auth_msg = random.choice(['', '', '', '', '知名UP主', '认证创作者', '签约主播']) if auth > 0 else ''
            create_date = rand_date(730, 0)

            user_rows.append((
                uid_counter, uname, pwd_hash, nickname, default_avatar, default_bg,
                sex, desc, exp, coin, vip, 0, 0, auth, auth_msg, create_date
            ))

        if user_rows:
            batch_insert(cursor, conn,
                "INSERT INTO user (uid,username,password,nickname,avatar,background,"
                "sex,description,exp,coin,vip,state,role,auth,auth_msg,create_date) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                user_rows, '用户')
        print(f'  📋 累计用户: {existing_user_count + len(user_rows)} 个')
    else:
        print(f'  ⏭️ 已有 {existing_user_count} 个用户，跳过')

    # 重新加载有效uid
    cursor.execute("SELECT uid FROM user WHERE state = 0")
    valid_uids = [r[0] for r in cursor.fetchall()]
    print(f'  📋 当前可用用户: {len(valid_uids)} 个')

    # ==================== 2. 加载视频数据(不动表) ====================
    print('\n🎬 [2/9] 加载已有视频数据(不修改)')
    cursor.execute("SELECT vid, duration FROM video WHERE status = 1")
    video_rows = cursor.fetchall()
    vid_list = [r[0] for r in video_rows]
    vid_duration = {r[0]: max(r[1], 10.0) for r in video_rows}
    print(f'  📋 已过审视频: {len(vid_list)} 个')
    if not vid_list:
        print('  ❌ 没有已过审视频，无法生成后续数据')
        return

    # ==================== 3. danmaku ====================
    print('\n🎞️ [3/9] 弹幕表 danmaku')
    cursor.execute("SELECT COUNT(*) FROM danmaku")
    existing_dm_count = cursor.fetchone()[0]
    TARGET_DM = 8000

    if existing_dm_count < TARGET_DM:
        need = TARGET_DM - existing_dm_count
        dm_batch = []
        for i in range(need):
            vid = random.choice(vid_list)
            uid = random.choice(valid_uids)
            duration = vid_duration[vid]
            time_point = round(min(random.triangular(0, duration, duration * 0.35), duration), 2)
            content = random.choice(DANMAKU_POOL)[:200]
            fontsize = random.choice(DM_FONTSIZES)
            mode = random.choices([1, 2, 3, 4], weights=[70, 10, 12, 8])[0]
            color = random.choice(DM_COLORS)
            state = random.choices([1, 2, 3], weights=[92, 5, 3])[0]

            dm_batch.append((
                vid, uid, content, fontsize, mode, color,
                time_point, state, rand_date(180, 0)
            ))

        batch_insert(cursor, conn,
            "INSERT INTO danmaku (vid,uid,content,fontsize,mode,color,`time`,state,create_date) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)", dm_batch, '弹幕')
    else:
        print(f'  ⏭️ 已有 {existing_dm_count} 条弹幕，跳过')

    # ==================== 4. follow ====================
    print('\n🤝 [4/9] 关注表 follow')
    cursor.execute("SELECT follower_uid, following_uid FROM follow")
    existing_follows = set((r[0], r[1]) for r in cursor.fetchall())
    # 用户多了之后不能枚举所有对，用随机采样
    TARGET_FOLLOW = 3000
    need_follow = max(0, TARGET_FOLLOW - len(existing_follows))

    if need_follow > 0:
        f_batch = []
        added = set()
        attempts = 0
        while len(f_batch) < need_follow and attempts < need_follow * 3:
            attempts += 1
            fu = random.choice(valid_uids)
            fg = random.choice(valid_uids)
            if fu == fg:
                continue
            pair = (fu, fg)
            if pair in existing_follows or pair in added:
                continue
            added.add(pair)
            f_batch.append((fu, fg, rand_date(365, 0)))

        if f_batch:
            batch_insert(cursor, conn,
                "INSERT INTO follow (follower_uid, following_uid, create_date) VALUES (%s,%s,%s)",
                f_batch, '关注')
    else:
        print(f'  ⏭️ 关注关系已足够 ({len(existing_follows)} 条)，跳过')

    # ==================== 5. favorite_folder 默认收藏夹 ====================
    print('\n📁 [5/9] 收藏夹表 favorite_folder (为每个用户创建默认收藏夹)')
    # 查询已有默认收藏夹的用户
    cursor.execute("SELECT DISTINCT uid FROM favorite_folder WHERE is_default = 1")
    users_with_default = set(r[0] for r in cursor.fetchall())
    users_need_folder = [uid for uid in valid_uids if uid not in users_with_default]

    if users_need_folder:
        folder_batch = []
        create_time = datetime.now()
        for uid in users_need_folder:
            folder_batch.append((uid, '默认收藏夹', None, None, 0, 1, create_time, create_time))
        batch_insert(cursor, conn,
            "INSERT INTO favorite_folder (uid,name,cover_url,description,video_count,is_default,create_time,update_time) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)", folder_batch, '默认收藏夹')
        # 标记用户已有默认收藏夹（如果user表有这个字段的话，没有也没关系）
    else:
        print(f'  ⏭️ 所有有效用户都已有默认收藏夹，跳过')

    # 建立 uid -> folder_id 映射，供 video_collect 使用
    cursor.execute("SELECT uid, id FROM favorite_folder WHERE is_default = 1")
    user_default_folder = {r[0]: r[1] for r in cursor.fetchall()}
    print(f'  📋 默认收藏夹映射: {len(user_default_folder)} 个用户')

    # ==================== 6. user_browse_history ====================
    print('\n📖 [6/9] 浏览历史表 user_browse_history')
    cursor.execute("SELECT COUNT(*) FROM user_browse_history")
    existing_hist = cursor.fetchone()[0]
    TARGET_HIST = 5000

    if existing_hist < TARGET_HIST:
        cursor.execute("SELECT uid, vid FROM user_browse_history")
        existing_pairs = set((r[0], r[1]) for r in cursor.fetchall())
        need_hist = TARGET_HIST - existing_hist
        h_batch = []
        added = set()
        attempts = 0
        while len(h_batch) < need_hist and attempts < need_hist * 3:
            attempts += 1
            uid = random.choice(valid_uids)
            vid = random.choice(vid_list)
            pair = (uid, vid)
            if pair in existing_pairs or pair in added:
                continue
            added.add(pair)
            progress = random.randint(0, int(vid_duration.get(vid, 300)))
            h_batch.append((uid, vid, rand_date(90, 0), progress, rand_date(90, 0)))

        if h_batch:
            batch_insert(cursor, conn,
                "INSERT INTO user_browse_history (uid,vid,browse_time,progress,create_date) "
                "VALUES (%s,%s,%s,%s,%s)", h_batch, '浏览历史')
    else:
        print(f'  ⏭️ 浏览历史已足够 ({existing_hist} 条)，跳过')

    # ==================== 7. video_like ====================
    print('\n👍 [7/9] 点赞表 video_like')
    cursor.execute("SELECT COUNT(*) FROM video_like")
    existing_like = cursor.fetchone()[0]
    TARGET_LIKE = 5000

    if existing_like < TARGET_LIKE:
        cursor.execute("SELECT uid, vid FROM video_like")
        existing_like_pairs = set((r[0], r[1]) for r in cursor.fetchall())
        need_like = TARGET_LIKE - existing_like
        l_batch = []
        added = set()
        attempts = 0
        while len(l_batch) < need_like and attempts < need_like * 3:
            attempts += 1
            uid = random.choice(valid_uids)
            vid = random.choice(vid_list)
            pair = (uid, vid)
            if pair in existing_like_pairs or pair in added:
                continue
            added.add(pair)
            l_batch.append((uid, vid, rand_date(180, 0)))

        if l_batch:
            batch_insert(cursor, conn,
                "INSERT INTO video_like (uid,vid,create_time) VALUES (%s,%s,%s)",
                l_batch, '点赞')
    else:
        print(f'  ⏭️ 点赞记录已足够 ({existing_like} 条)，跳过')

    # ==================== 8. video_coin ====================
    print('\n🪙 [8/9] 投币表 video_coin')
    cursor.execute("SELECT COUNT(*) FROM video_coin")
    existing_coin = cursor.fetchone()[0]
    TARGET_COIN = 2000

    if existing_coin < TARGET_COIN:
        cursor.execute("SELECT uid, vid FROM video_coin")
        existing_coin_pairs = set((r[0], r[1]) for r in cursor.fetchall())
        need_coin = TARGET_COIN - existing_coin
        c_batch = []
        added = set()
        attempts = 0
        while len(c_batch) < need_coin and attempts < need_coin * 3:
            attempts += 1
            uid = random.choice(valid_uids)
            vid = random.choice(vid_list)
            pair = (uid, vid)
            if pair in existing_coin_pairs or pair in added:
                continue
            added.add(pair)
            c_batch.append((uid, vid, rand_date(180, 0)))

        if c_batch:
            batch_insert(cursor, conn,
                "INSERT INTO video_coin (uid,vid,create_time) VALUES (%s,%s,%s)",
                c_batch, '投币')
    else:
        print(f'  ⏭️ 投币记录已足够 ({existing_coin} 条)，跳过')

    # ==================== 9. video_collect ====================
    print('\n⭐ [9/9] 收藏表 video_collect')
    cursor.execute("SELECT COUNT(*) FROM video_collect")
    existing_collect = cursor.fetchone()[0]
    TARGET_COLLECT = 2000

    if existing_collect < TARGET_COLLECT:
        cursor.execute("SELECT uid, vid, folder_id FROM video_collect")
        existing_collect_pairs = set((r[0], r[1], r[2]) for r in cursor.fetchall())
        need_collect = TARGET_COLLECT - existing_collect
        cl_batch = []
        added = set()
        attempts = 0
        while len(cl_batch) < need_collect and attempts < need_collect * 3:
            attempts += 1
            uid = random.choice(valid_uids)
            vid = random.choice(vid_list)
            folder_id = user_default_folder.get(uid)
            if not folder_id:
                continue
            pair = (uid, vid, folder_id)
            if pair in existing_collect_pairs or pair in added:
                continue
            added.add(pair)
            cl_batch.append((uid, vid, folder_id, rand_date(180, 0)))

        if cl_batch:
            batch_insert(cursor, conn,
                "INSERT INTO video_collect (uid,vid,folder_id,create_time) VALUES (%s,%s,%s,%s)",
                cl_batch, '收藏')
        # 同步每个用户默认收藏夹的 video_count
        cursor.execute("""
            UPDATE favorite_folder ff SET video_count = (
                SELECT COUNT(*) FROM video_collect vc
                WHERE vc.uid = ff.uid AND vc.folder_id = ff.id
            ) WHERE ff.is_default = 1
        """)
        conn.commit()
        print('  ✅ 已同步收藏夹视频数量')
    else:
        print(f'  ⏭️ 收藏记录已足够 ({existing_collect} 条)，跳过')

    # ==================== 同步 video_stat ====================
    print('\n🔄 同步 video_stat 统计数据')
    cursor.execute("""
        UPDATE video_stat vs SET
            vs.view = (SELECT COUNT(*) FROM user_browse_history h WHERE h.vid = vs.vid),
            vs.danmaku = (SELECT COUNT(*) FROM danmaku d WHERE d.vid = vs.vid AND d.state = 1),
            vs.reply = (SELECT COUNT(*) FROM comment c WHERE c.vid = vs.vid AND c.is_deleted = 0),
            vs.`like` = (SELECT COUNT(*) FROM video_like l WHERE l.vid = vs.vid),
            vs.coin = (SELECT COUNT(*) FROM video_coin co WHERE co.vid = vs.vid),
            vs.favorite = (SELECT COUNT(*) FROM video_collect cl WHERE cl.vid = vs.vid)
    """)
    conn.commit()
    print('  ✅ video_stat 已同步')

    # ==================== 同步用户到 ES ====================
    print('\n🔍 同步用户数据到 Elasticsearch')
    try:
        es = Elasticsearch(**ES_CONFIG)
        es.info()  # 测试连接
        # 查询所有需要同步的用户（全量同步确保ES与MySQL一致）
        cursor2 = conn.cursor()
        cursor2.execute("SELECT uid, username, avatar, description FROM user WHERE state = 0")
        user_docs = cursor2.fetchall()
        cursor2.close()

        def gen_actions():
            for row in user_docs:
                yield {
                    '_index': ES_USER_INDEX,
                    '_id': str(row[0]),  # 用uid作为文档ID
                    '_source': {
                        'uid': row[0],
                        'username': row[1],
                        'avatar': row[2] or '',
                        'description': row[3] or ''
                    }
                }

        success, errors = bulk(es, gen_actions(), chunk_size=BATCH_SIZE, raise_on_error=True)
        print(f'  ✅ 已同步 {success} 个用户到 ES 索引 [{ES_USER_INDEX}]')
        if errors:
            print(f'  ⚠️ 同步失败: {len(errors)} 条')
    except Exception as e:
        print(f'  ❌ ES同步失败: {e}')
        print(f'  💡 请确保 Elasticsearch 正在运行且可访问')

    # ==================== 汇总 ====================
    print('\n' + '=' * 50)
    print('📊 数据汇总:')
    tables = ['category', 'user', 'video', 'video_stat', 'comment', 'danmaku',
              'favorite_folder', 'follow', 'user_browse_history', 'video_like', 'video_coin', 'video_collect']
    for t in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {t}")
        cnt = cursor.fetchone()[0]
        print(f'  {t:25s}: {cnt:>8} 条')
    print('=' * 50)
    print('🎉 全部完成！')

    cursor.close()
    conn.close()


if __name__ == '__main__':
    main()
