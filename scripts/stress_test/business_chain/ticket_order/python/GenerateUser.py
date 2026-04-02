import requests
import random
import string
import time
from typing import Dict, Set

# ===================== 核心配置 =====================
API_URL = "http://127.0.0.1:8080/api/user-service/register"
REQUEST_HEADERS = {"Content-Type": "application/json"}
TIMEOUT = 10
REQUEST_INTERVAL = 0.1
TOTAL_NEED_INSERT = 10000

# 去重：防止用户名/手机号/身份证重复
USED_USERNAMES: Set[str] = set()
USED_PHONES: Set[str] = set()
USED_IDCARDS: Set[str] = set()


# ===================== 生成随机用户数据 =====================
def generate_chinese_name() -> str:
    surnames = ["赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈", "褚", "卫", "蒋", "沈", "韩", "杨"]
    given_names = ["伟", "芳", "娜", "敏", "静", "强", "磊", "洋", "杰", "娟", "涛", "明", "华", "颖", "浩", "婷"]
    return random.choice(surnames) + ''.join(random.choices(given_names, k=random.randint(1, 2)))


def generate_id_card() -> str:
    area_codes = ["110101", "310101", "440101", "510101", "120101", "330101"]
    birth_date = f"{random.randint(1980, 2000)}{random.randint(1, 12):02d}{random.randint(1, 28):02d}"
    return f"{random.choice(area_codes)}{birth_date}{random.randint(100, 999)}{random.choice('0123456789X')}"


def generate_random_user_data() -> Dict:
    # 唯一用户名
    while True:
        username = ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))
        if username not in USED_USERNAMES:
            USED_USERNAMES.add(username)
            break
    # 唯一手机号
    while True:
        phone = "1" + random.choice("345789") + ''.join(random.choices(string.digits, k=9))
        if phone not in USED_PHONES:
            USED_PHONES.add(phone)
            break
    # 唯一身份证
    while True:
        id_card = generate_id_card()
        if id_card not in USED_IDCARDS:
            USED_IDCARDS.add(id_card)
            break

    return {
        "username": username,
        "password": "123456",  # 固定密码，简化测试
        "realName": generate_chinese_name(),
        "idType": 0,
        "idCard": id_card,
        "phone": phone,
        "email": f"{username}@test.com"
    }


# ===================== 调用注册接口（仅判断成功） =====================
def insert_user(user_data: Dict) -> bool:
    try:
        response = requests.post(API_URL, json=user_data, headers=REQUEST_HEADERS, timeout=TIMEOUT)

        # ✅ 核心修改：只判断 success=true，不读取任何返回数据
        if response.status_code == 200 and response.json().get("success") is True:
            return True
        return False

    except Exception:
        return False


# ===================== 批量注册 =====================
def batch_register_users():
    success, fail = 0, 0
    print(f"开始批量插入 {TOTAL_NEED_INSERT} 条用户数据...")
    start_time = time.time()

    for i in range(TOTAL_NEED_INSERT):
        if insert_user(generate_random_user_data()):
            success += 1
        else:
            fail += 1

        # 进度打印
        if (i + 1) % 100 == 0:
            print(f"已完成 {i + 1}/{TOTAL_NEED_INSERT} | 成功:{success} 失败:{fail}")
        time.sleep(REQUEST_INTERVAL)

    # 结果统计
    print("=" * 50)
    print(f"执行完成 | 总耗时:{round(time.time() - start_time, 2)}s")
    print(f"成功:{success} | 失败:{fail} | 成功率:{success / (success + fail) * 100:.2f}%")


if __name__ == "__main__":
    # 安装依赖：pip install requests
    batch_register_users()