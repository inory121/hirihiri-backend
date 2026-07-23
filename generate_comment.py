import pymysql
import random
from datetime import datetime, timedelta

conn = pymysql.connect(host='localhost', user='root', password='12345', database='hirihiri')
cursor = conn.cursor()

# ── 真实评论模板池 ──
COMMENT_TEMPLATES = [
    '这个视频太棒了，up主辛苦了！',
    '哈哈哈笑死我了',
    '前排占座',
    '说得太对了，深有同感',
    '催更催更！',
    '这个观点很新颖，学到了',
    'up主讲得很清楚，感谢分享',
    '看到最后绷不住了',
    '建议出一期续集',
    '这质量也太高了吧',
    '第一次看这种类型，感觉不错',
    '弹幕护体！',
    '来了来了，终于更新了',
    '有一说一，这个确实厉害',
    '收藏了，回头慢慢看',
    '这个bgm是什么？好好听',
    '画面太美了',
    '看完感觉整个人都不好了',
    '谁懂啊，看到这里直接泪目',
    '这段我反复看了好几遍',
    '大佬带带我',
    '虽然不太懂但是感觉很厉害',
    '有没有课代表总结一下',
    '这个系列我追定了',
    '投币投币',
    '下次能不能讲讲这个话题的进阶内容？',
    '我居然看完了，还挺有意思',
    '开头那个梗太经典了',
    'up主越来越会做了',
    '好家伙，这也太细节了',
    '评论区有人才啊',
    '这个结尾我没想到',
    '建议up多出这种类型的视频',
    '三刷了，每次都有新发现',
    '确实，我也是这么觉得的',
    '你说得对，但是我觉得还有一点要考虑',
    '不同意楼上的观点，我觉得应该从另一个角度看',
    '这个数据有出处吗？想了解一下',
    '太真实了，简直就是我的日常',
    '每次心情不好的时候就来看这个系列',
    '请问up主用的是什么软件/工具？',
    '感觉这个领域最近发展好快',
    '已关注，期待更多内容',
    '这期干货满满，做笔记了',
    '虽然很短但是信息量很大',
    '终于有人讲这个了！',
    '看完之后马上去试了一下，真的可以',
    '这个系列从第一期追到现在，感慨万千',
    '能不能出一个入门教程？',
    '笑到邻居来敲门',
]

# ── 回复用短评模板 ──
REPLY_TEMPLATES = [
    '确实', '同意', '哈哈', '+1', '笑死', '真的假的？',
    '说得对', '我也这么觉得', '大佬说得对', '学到了',
    '太对了', '绷不住了', '好家伙', '6', 'nb',
    '太强了', '真的吗', '细说', '展开说说', '催更',
    '懂了', '原来如此', '涨知识了', '支持！', '顶',
    '没错没错', '太真实了', 'awsl', '哈哈哈是的', '有道理',
    '这个角度没想到', 'up回复我！', '同问', '蹲一个回复',
]


def gen_power_law_like():
    """幂律分布生成点赞数：大部分评论0~5，少量可达数千"""
    r = random.random()
    if r < 0.50:
        return random.randint(0, 3)
    elif r < 0.75:
        return random.randint(4, 20)
    elif r < 0.90:
        return random.randint(21, 100)
    elif r < 0.97:
        return random.randint(101, 500)
    else:
        return random.randint(501, 3000)


def gen_dislike(like_count):
    """点踩与点赞正相关但远小于点赞"""
    if like_count <= 3:
        return random.randint(0, 1)
    return random.randint(0, max(1, like_count // 10))


def gen_time_offset():
    """时间偏移：越近的时间评论越密集（指数衰减）"""
    # 大部分评论集中在近期，少量较早
    days_ago = int(random.expovariate(1.0 / 30))  # 均值30天的指数分布
    days_ago = min(days_ago, 180)  # 最多180天前
    hours_offset = random.randint(0, 23)
    minutes_offset = random.randint(0, 59)
    return timedelta(days=days_ago, hours=hours_offset, minutes=minutes_offset)


# 1. 获取当前最大id，确保不重复
cursor.execute("SELECT MAX(id) FROM comment")
max_id = cursor.fetchone()[0] or 0

# 2. 加载已有评论的树形关系 {id: {'root_id': root_id, 'vid': vid}}
#    新插入的数据也会动态加入这个字典，保证后续引用合法
cursor.execute("SELECT id, root_id, vid FROM comment")
comment_info = {row[0]: {'root_id': row[1], 'vid': row[2]} for row in cursor.fetchall()}

batch = []
BATCH_SIZE = 1000
GEN_COUNT = 5000  # 要生成的数量

for i in range(GEN_COUNT):
    current_id = max_id + i + 1
    is_root = random.random() < 0.3  # 30%概率为根评论
    
    if is_root:
        # ✅ 根评论：root_id=0, parent_id=0, vid随机
        root_id = 0
        parent_id = 0
        vid = random.randint(1, 10)
        content = random.choice(COMMENT_TEMPLATES)
    else:
        # ✅ 子评论：从已有评论中随机选一个作为 parent
        parent_id = random.choice(list(comment_info.keys()))
        parent = comment_info[parent_id]
        # ✅ 核心修正：继承父评论的 root_id；如果父评论本身就是根评论，则 root_id = 父评论id
        parent_root = parent['root_id']
        root_id = parent_root if parent_root != 0 else parent_id
        # ✅ 关键修复：子评论继承父评论的 vid，确保同一棵树在同一个视频下
        vid = parent['vid']
        # 回复评论 60%用短评，40%用长评模板
        content = random.choice(REPLY_TEMPLATES) if random.random() < 0.6 else random.choice(COMMENT_TEMPLATES)
    
    # 将当前评论加入树结构，供后续评论引用
    comment_info[current_id] = {'root_id': root_id, 'vid': vid}
    
    like_count = gen_power_law_like()
    
    batch.append((
        vid,                           # vid（根评论随机，子评论继承父评论）
        random.randint(1, 8),        # uid (1-8)
        root_id,
        parent_id,
        random.randint(1, 8),        # to_user_id (1-8)
        content,
        like_count,
        gen_dislike(like_count),
        datetime.now() - gen_time_offset(),
        1 if random.random() < 0.02 else 0,  # is_top
        0                            # is_deleted
    ))
    
    # 批量提交
    if len(batch) >= BATCH_SIZE:
        cursor.executemany(
            "INSERT INTO comment (vid,uid,root_id,parent_id,to_user_id,content,`like`,dislike,create_date,is_top,is_deleted) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", batch
        )
        conn.commit()
        batch = []

# 提交剩余数据
if batch:
    cursor.executemany(
        "INSERT INTO comment (vid,uid,root_id,parent_id,to_user_id,content,`like`,dislike,create_date,is_top,is_deleted) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)", batch
    )
    conn.commit()

cursor.close()
conn.close()
print(f"✅ 成功插入 {GEN_COUNT} 条评论，id范围: {max_id+1} ~ {max_id+GEN_COUNT}")