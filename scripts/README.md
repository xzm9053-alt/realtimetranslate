# 自动化更新推送脚本使用说明

## 脚本功能

`push_update.py` 用于在作者发布新版本视频后，生成或一键推送 `version.json` 到腾讯云 COS / 阿里云 OSS 对象存储。

## 使用方法

### 1. 导出本地 JSON（手动上传）
```bash
python scripts/push_update.py --code 2 --name "v0.2.0" --url "https://b23.tv/xxx" --msg "全新版本已上线！"
```
会在当前目录生成 `version.json`，可直接登录腾讯云控制台网页手动上传覆盖。

### 2. 一键自动推送到腾讯云 COS
配置环境变量（可写入本地环境或由助手代执行）：
- `TENCENT_COS_SECRET_ID`: 腾讯云 API 密钥 SecretId
- `TENCENT_COS_SECRET_KEY`: 腾讯云 API 密钥 SecretKey
- `TENCENT_COS_BUCKET`: 存储桶名称（如 `live-update-1250000000`）
- `TENCENT_COS_REGION`: 存储桶地域（默认 `ap-shanghai`）

依赖安装（如需自动上传）：
```bash
pip install -U cos-python-sdk-v5
```

执行一键推送：
```bash
python scripts/push_update.py --code 2 --name "v0.2.0" --url "https://b23.tv/xxx" --msg "全新版本已上线！" --upload
```
