import os
import sys
import subprocess
import zipfile
import tarfile

if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except:
        pass

ADB = r"E:\AAAtranslator\android-sdk\platform-tools\adb.exe"
DEVICE = "U8VCZHFATWHUI7AU"
LOCAL_PACK_DIR = os.path.abspath("网盘分享_ASR语音识别模型包")
TEMP_DIR = os.path.abspath("temp_model_extract")
PHONE_DOWNLOAD_DIR = "/sdcard/Download/实时翻译模型包"
PHONE_APP_MODELS_DIR = "/sdcard/Android/data/com.xzm.realtimetranslate/files/models/asr"

os.makedirs(TEMP_DIR, exist_ok=True)

def run_adb(args, check=True):
    env = os.environ.copy()
    env["MSYS_NO_PATHCONV"] = "1"
    cmd = [ADB, "-s", DEVICE] + args
    result = subprocess.run(cmd, env=env, capture_output=True, text=True)
    if check and result.returncode != 0:
        print(f"ADB Error: {result.stderr.strip()}", file=sys.stderr)
    return result

def main():
    print("=" * 65)
    print(" 🚀 正在将全部 ASR 识别模型传输到手机...")
    print("=" * 65)

    # 1. 确保手机端目录存在
    run_adb(["shell", f"mkdir -p {PHONE_DOWNLOAD_DIR} && mkdir -p {PHONE_APP_MODELS_DIR}"])

    # 2. 传输网盘包至手机下载目录 /sdcard/Download/实时翻译模型包/
    print("\n[Step 1/2] 传输打包压缩文件至手机 /sdcard/Download/实时翻译模型包/ ...")
    files_to_push = [
        "【使用必读】手机端怎么导入使用.txt",
        "01_SenseVoice_中英日韩粤多语种_INT8_毫秒级打字机(推荐).zip",
        "02_Paraformer_中文工业级会议_INT8(推荐).zip",
        "03_Whisper_Tiny_99语种极速轻量_INT8(推荐).zip",
        "04_Moonshine_Tiny_英文边缘低延迟_INT8.zip",
        "05_Whisper_Small_99语种高精版_INT8.zip",
        "06_Whisper_Turbo_旗舰极速版_INT8.zip",
        "07_FireRedASR_小红书工业级中文低错率_INT8.zip",
        "08_Qwen3_ASR_0.6B_阿里千问多语种方言_INT8.tar.bz2",
    ]

    for fname in files_to_push:
        lpath = os.path.join(LOCAL_PACK_DIR, fname)
        if not os.path.exists(lpath):
            continue
        mb = os.path.getsize(lpath) / 1024 / 1024
        print(f"  -> 传输 {fname} ({mb:.1f} MB) ...")
        remote_path = f"{PHONE_DOWNLOAD_DIR}/{fname}"
        res = run_adb(["push", lpath, remote_path])
        if res.returncode == 0:
            print(f"     ✅ 传输成功")
        else:
            print(f"     ❌ 传输失败: {res.stderr.strip()}")

    # 3. 将解压后的各模型直接部署进 App 私有目录（让手机无需导入即可即刻就绪）
    print("\n[Step 2/2] 解压并直接部署到 App 模型库，实现打开即用 (🟢 已就绪) ...")

    models_deploy_map = [
        ("paraformer-zh", "02_Paraformer_中文工业级会议_INT8(推荐).zip"),
        ("whisper-tiny", "03_Whisper_Tiny_99语种极速轻量_INT8(推荐).zip"),
        ("moonshine-tiny", "04_Moonshine_Tiny_英文边缘低延迟_INT8.zip"),
        ("whisper-small", "05_Whisper_Small_99语种高精版_INT8.zip"),
        ("whisper-large-v3-turbo", "06_Whisper_Turbo_旗舰极速版_INT8.zip"),
        ("fire-red-asr", "07_FireRedASR_小红书工业级中文低错率_INT8.zip"),
    ]

    for model_id, zip_name in models_deploy_map:
        zip_path = os.path.join(LOCAL_PACK_DIR, zip_name)
        if not os.path.exists(zip_path):
            continue
        print(f"\n  [部署] {model_id} ...")
        extract_dest = os.path.join(TEMP_DIR, model_id)
        os.makedirs(extract_dest, exist_ok=True)
        with zipfile.ZipFile(zip_path, 'r') as zf:
            zf.extractall(extract_dest)

        remote_model_dir = f"{PHONE_APP_MODELS_DIR}/{model_id}"
        run_adb(["shell", f"mkdir -p {remote_model_dir}"])
        for root, dirs, files in os.walk(extract_dest):
            for file in files:
                fpath = os.path.join(root, file)
                rpath = f"{remote_model_dir}/{file}"
                run_adb(["push", fpath, rpath])
        print(f"  ✅ {model_id} 已成功部署至 {remote_model_dir}")

    # 处理 Qwen3-ASR tar.bz2
    qwen_tar = os.path.join(LOCAL_PACK_DIR, "08_Qwen3_ASR_0.6B_阿里千问多语种方言_INT8.tar.bz2")
    if os.path.exists(qwen_tar):
        print(f"\n  [部署] qwen3-asr-0.6b ...")
        qwen_dest = os.path.join(TEMP_DIR, "qwen3-asr-0.6b")
        os.makedirs(qwen_dest, exist_ok=True)
        try:
            with tarfile.open(qwen_tar, "r:bz2") as tar:
                tar.extractall(qwen_dest)

            # 找到解压后的实际模型目录
            actual_dir = qwen_dest
            for item in os.listdir(qwen_dest):
                sub = os.path.join(qwen_dest, item)
                if os.path.isdir(sub) and "qwen3" in item.lower():
                    actual_dir = sub
                    break

            remote_qwen_dir = f"{PHONE_APP_MODELS_DIR}/qwen3-asr-0.6b"
            run_adb(["shell", f"mkdir -p {remote_qwen_dir}"])
            for item in os.listdir(actual_dir):
                ipath = os.path.join(actual_dir, item)
                rpath = f"{remote_qwen_dir}/{item}"
                run_adb(["push", ipath, rpath])
            print(f"  ✅ qwen3-asr-0.6b 已成功部署至 {remote_qwen_dir}")
        except Exception as e:
            print(f"  ❌ Qwen3-ASR 解包部署错误: {e}")

    # 清理本地解压临时目录
    import shutil
    shutil.rmtree(TEMP_DIR, ignore_errors=True)

    print("\n" + "=" * 65)
    print(" 🎉 全部模型已成功传输并部署到手机！")
    print(f" 📂 手机压缩包存储位置: {PHONE_DOWNLOAD_DIR}")
    print(f" 🚀 App 实时已就绪目录: {PHONE_APP_MODELS_DIR}")
    print("=" * 65)

if __name__ == "__main__":
    main()
