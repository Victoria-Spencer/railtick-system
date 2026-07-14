# docker下载命令依赖虚拟机版本

# 1. 创建docker配置目录（如不存在）

sudo mkdir -p /etc/docker

# 2. 一键写入你的加速器配置（覆盖原有文件）

sudo tee /etc/docker/daemon.json <<-'EOF'
{
"registry-mirrors": [
"https://xxx.mirror.swr.myhuaweicloud.com（华为云能用）"
]
}
EOF

# 3. 重载配置并重启Docker服务

sudo systemctl daemon-reload
sudo systemctl restart docker

# 4. 验证配置是否生效

docker info | grep -A 5 "Registry Mirrors"
