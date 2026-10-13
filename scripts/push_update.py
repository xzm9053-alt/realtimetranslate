#!/usr/bin/env python3
"""
一键更新与推送 version.json 到腾讯云 COS / 阿里云 OSS。
用法：
  python scripts/push_update.py --code 2 --name "v0.2.0" --url "https://b23.tv/xxx" --msg "全新版本已上线！"
"""

import argparse
import json
import os
import sys

# Windows 终端编码容错处理
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

def main():
    parser = argparse.ArgumentParser(description="发布新版本更新通知到云端")
    parser.add_argument("--code", type=int, required=True, help="最新版本代码 (versionCode, 整数)")
    parser.add_argument("--name", type=str, required=True, help="最新版本名称 (如 v0.2.0)")
    parser.add_argument("--url", type=str, required=True, help="引流跳转视频链接 (如 https://b23.tv/xxx)")
    parser.add_argument("--title", type=str, default="🎉 发现全新版本！", help="弹窗标题")
    parser.add_argument("--msg", type=str, default="优化了核心体验，快来看看 UP 主的最新演示视频吧！", help="更新说明")
    parser.add_argument("--output", type=str, default="version.json", help="本地导出文件路径")
    parser.add_argument("--upload", action="store_true", help="是否直接通过腾讯云 COS 上传")

    args = parser.parse_args()

    payload = {
        "versionCode": args.code,
        "versionName": args.name,
        "title": args.title,
        "message": args.msg,
        "videoUrl": args.url,
        "forceUpdate": False,
    }

    content = json.dumps(payload, ensure_ascii=False, indent=2)
    with open(args.output, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"✅ 已成功生成本地版本配置文件: {args.output}")
    print(content)

    if args.upload:
        secret_id = os.environ.get("TENCENT_COS_SECRET_ID")
        secret_key = os.environ.get("TENCENT_COS_SECRET_KEY")
        bucket = os.environ.get("TENCENT_COS_BUCKET")
        region = os.environ.get("TENCENT_COS_REGION", "ap-shanghai")

        if not (secret_id and secret_key and bucket):
            print("⚠️ 未检测到 TENCENT_COS_SECRET_ID/SECRET_KEY/BUCKET 环境变量，已保留本地文件，请手动上传至存储桶。")
            return

        try:
            from qcloud_cos import CosConfig, CosS3Client
            config = CosConfig(Region=region, SecretId=secret_id, SecretKey=secret_key)
            client = CosS3Client(config)
            client.put_object(
                Bucket=bucket,
                Body=content.encode("utf-8"),
                Key="translate/version.json",
                ContentType="application/json",
            )
            print("🚀 已成功自动推送覆盖到腾讯云 COS！全网生效。")
        except ImportError:
            print("⚠️ 未安装 cos-python-sdk-v5 库。可通过 pip install -U cos-python-sdk-v5 安装后直接一键上传。")
        except Exception as e:
            print(f"❌ 上传失败: {e}")

if __name__ == "__main__":
    main()
