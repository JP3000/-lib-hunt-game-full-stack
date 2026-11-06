# 云服务器部署文档

> **⚠️ 本文档描述的是改造前的纯前端版本**（PM2 + 本地 JSON 文件存储），已不适用于当前代码。
>
> 当前仓库是 Next.js + Spring Boot 全栈系统，完整的全栈部署方案尚未落地（见 [PLAN.md](PLAN.md) 阶段 6）。
> 在此之前，请按 [README](README.md#快速开始) 在本地运行三个组件：MySQL/Redis（docker compose）、Spring Boot、Next.js。


## 环境信息

| 项目 | 内容 |
|------|------|
| 服务器 | 腾讯云轻量云服务器 |
| 操作系统 | OpenCloudOS 9.2 |
| Node.js | v22.12.0 |
| 域名 | libhuntgame.com.cn |
| 部署方式 | PM2 + Nginx + 本地 JSON 文件存储 |

---

## 一、环境准备

```bash
# 安装 Git
sudo yum install git -y

# 安装 Nginx
sudo yum install nginx -y

# 确认 Node.js 版本（需要 ≥20）
node -v
```

---

## 二、部署应用

```bash
# 克隆代码
git clone https://github.com/JP3000/lib-hunt-game.git
cd lib-hunt-game

# 安装依赖
npm install

# 配置本地文件存储模式
echo "STORAGE_MODE=file" > .env.local

# 构建
npm run build

# 安装 PM2 并常驻启动
npm install -g pm2
pm2 start npm --name "lib-hunt" -- start
pm2 save
```

验证：`curl http://localhost:3000` 应返回 HTML。

---

## 三、Nginx 反向代理

创建 `/etc/nginx/conf.d/lib-hunt.conf`：

```nginx
server {
    listen 80;
    server_name libhuntgame.com.cn;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name libhuntgame.com.cn;

    ssl_certificate     /etc/letsencrypt/live/libhuntgame.com.cn/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/libhuntgame.com.cn/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
# 重载 Nginx 主配置（去掉默认 80 端口 server 块）
sudo bash -c 'cat > /etc/nginx/nginx.conf << '\''EOF'\''
user nginx;
worker_processes auto;
error_log /var/log/nginx/error.log notice;
pid /run/nginx.pid;

events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;
    access_log    /var/log/nginx/access.log;

    sendfile        on;
    keepalive_timeout  65;

    include /etc/nginx/conf.d/*.conf;
}
EOF'

sudo nginx -t && sudo systemctl start nginx
```

---

## 四、HTTPS 证书

```bash
# 安装 certbot
sudo yum install certbot -y

# 腾讯云 Lighthouse 面板默认占 80 端口，需先释放
# 把 Lighthouse 从 80 改为 8081
sudo sed -i "s/port = 80/port = 8081/" /usr/local/lighthouse/softwares/nodejs/app/app.js
kill $(pgrep -f lighthouse/app.js)

# 确认 80 端口已空
ss -tlnp | grep :80

# 申请证书（standalone 模式需要 80 端口空闲）
sudo certbot certonly --standalone -d libhuntgame.com.cn

# 启动 Nginx
sudo systemctl start nginx
```

证书有效期 90 天，certbot 会自动续期，无需手动操作。

---

## 五、防火墙

腾讯云控制台 → 轻量云服务器 → 防火墙，确保放行：

| 端口 | 用途 |
|------|------|
| 80 | HTTP |
| 443 | HTTPS |
| 8080 | 临时测试（可删除） |

---

## 六、日常运维

```bash
# 更新代码
cd ~/lib-hunt-game
git pull
npm run build
pm2 restart lib-hunt

# 查看应用状态
pm2 status

# 查看日志
pm2 logs lib-hunt

# 重启 Nginx
sudo systemctl reload nginx

# 查看证书状态
sudo certbot certificates
```

---

## 七、数据备份

`game-results.json` 在项目根目录，建议定期备份：

```bash
# 手动备份
cp ~/lib-hunt-game/game-results.json ~/backup/game-results-$(date +%Y%m%d).json

# 定时备份（每天凌晨 3 点）
crontab -e
# 添加：
0 3 * * * cp ~/lib-hunt-game/game-results.json ~/backup/game-results-$(date +\%Y\%m\%d).json
```

---

## 八、故障排查

```bash
# 检查应用是否在跑
pm2 status

# 检查 3000 端口
ss -tlnp | grep :3000

# 检查 Nginx 状态
sudo systemctl status nginx

# 检查 Nginx 配置
sudo nginx -t

# 查看 Nginx 错误日志
sudo tail -20 /var/log/nginx/error.log
```
