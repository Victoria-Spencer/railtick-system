#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从MySQL用户表提取 username + password 并写入CSV
适配 railtick-system 压测脚本目录结构
"""
import pymysql
import csv
import os

# ===================== 【配置项：和你的配置完全一致】 =====================
# MySQL 数据库配置
DB_CONFIG = {
    "host": "192.168.43.143",
    "port": 3306,
    "user": "root",
    "password": "root",
    "db": "rs-order",       # 你的数据库名
    "charset": "utf8mb4"
}

# 输出文件路径（自动定位到压测链路的 data 目录）
BASE_DIR = os.path.dirname(os.path.abspath(__file__))  # 当前脚本所在目录
OUTPUT_CSV = os.path.join(BASE_DIR, "../data/users.csv")

# SQL 查询语句（提取用户名和密码）
SQL = "SELECT username, password FROM user;"

# ===================== 【核心执行逻辑】 =====================
def extract_users_to_csv():
    # 1. 自动创建 data 目录（不存在则创建）
    data_dir = os.path.dirname(OUTPUT_CSV)
    if not os.path.exists(data_dir):
        os.makedirs(data_dir)
        print(f"✅ 自动创建目录：{data_dir}")

    # 2. 连接 MySQL 数据库
    try:
        connection = pymysql.connect(**DB_CONFIG)
        cursor = connection.cursor()
        print("✅ 成功连接 MySQL 数据库")

        # 3. 执行查询
        cursor.execute(SQL)
        user_data = cursor.fetchall()
        print(f"✅ 查询到 {len(user_data)} 条用户数据")

        # 4. 写入 CSV 文件
        with open(OUTPUT_CSV, "w", newline="", encoding="utf-8-sig") as f:
            writer = csv.writer(f)
            # 写入表头
            writer.writerow(["username", "password"])
            # 写入数据
            writer.writerows(user_data)

        print(f"✅ 数据已成功写入：{os.path.abspath(OUTPUT_CSV)}")

    except Exception as e:
        print(f"❌ 执行失败：{str(e)}")
    finally:
        # 关闭连接
        if 'connection' in locals() and connection.open:
            cursor.close()
            connection.close()
            print("✅ MySQL 连接已关闭")

if __name__ == '__main__':
    extract_users_to_csv()