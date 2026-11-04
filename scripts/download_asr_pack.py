import os
import sys
import time
import zipfile
import urllib.request
from urllib.error import URLError

if sys.platform == 'win32':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except:
        pass

OUTPUT_DIR = os.path.abspath("网盘分享_ASR语音识别模型包")
CACHE_DIR = os.path.abspath("model_download_cache")

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.makedirs(CACHE_DIR, exist_ok=True)

# 代理配置（如果本机 127.0.0.1:7897 开启）
PROXY_HANDLER = urllib.request.ProxyHandler({'http': 'http://127.0.0.1:7897', 'https': 'http://127.0.0.1:7897'})
DIRECT_OPENER = urllib.request.build_opener()
PROXY_OPENER = urllib.request.build_opener(PROXY_HANDLER)

def download_file(url, dest_path, use_proxy=False, desc=""):
    if os.path.exists(dest_path) and os.path.getsize(dest_path) > 0:
        print(f"  [已存在跳过] {os.path.basename(dest_path)} ({os.path.getsize(dest_path)/1024/1024:.1f} MB)")
        return True

    print(f"  [正在下载] {desc or os.path.basename(dest_path)} ...")
    temp_path = dest_path + ".tmp"

    opener = PROXY_OPENER if use_proxy else DIRECT_OPENER
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})

    start_time = time.time()
    last_print = start_time
    downloaded = 0

    try:
        with opener.open(req, timeout=30) as resp, open(temp_path, "wb") as out:
            total_size = resp.headers.get('Content-Length')
            total_size = int(total_size) if total_size and total_size.isdigit() else 0

            while True:
                chunk = resp.read(1024 * 1024) # 1MB chunks
                if not chunk:
                    break
                out.write(chunk)
                downloaded += len(chunk)

                now = time.time()
                if now - last_print >= 2.0:
                    elapsed = now - start_time
                    speed_mb = (downloaded / (1024 * 1024)) / (elapsed + 0.001)
                    if total_size > 0:
                        percent = (downloaded / total_size) * 100
                        print(f"    -> {downloaded/1024/1024:.1f}/{total_size/1024/1024:.1f} MB ({percent:.1f}%) | 速度: {speed_mb:.2f} MB/s")
                    else:
                        print(f"    -> 已下载 {downloaded/1024/1024:.1f} MB | 速度: {speed_mb:.2f} MB/s")
                    last_print = now

        if os.path.exists(temp_path):
            os.replace(temp_path, dest_path)
            total_mb = os.path.getsize(dest_path) / (1024 * 1024)
            elapsed = time.time() - start_time
            print(f"  [完成] {os.path.basename(dest_path)} ({total_mb:.1f} MB, 耗时 {elapsed:.1f}s)")
            return True
    except Exception as e:
        print(f"  [失败] 下载出错: {e}")
        if os.path.exists(temp_path):
            try: os.remove(temp_path)
            except: pass
        return False

def package_zip(zip_filename, files_map):
    zip_path = os.path.join(OUTPUT_DIR, zip_filename)
    if os.path.exists(zip_path) and os.path.getsize(zip_path) > 1024 * 1024:
        print(f"  [压缩包已就绪] {zip_filename} ({os.path.getsize(zip_path)/1024/1024:.1f} MB)")
        return zip_path

    print(f"  [正在打包 ZIP] {zip_filename} ...")
    with zipfile.ZipFile(zip_path, 'w', compression=zipfile.ZIP_STORED) as zf:
        for arcname, local_path in files_map.items():
            zf.write(local_path, arcname)
    print(f"  [打包完成] {zip_filename} ({os.path.getsize(zip_path)/1024/1024:.1f} MB)")
    return zip_path

def main():
    print("=" * 65)
    print(" 🚀 开始批量下载端侧开源 ASR 语音识别模型包 (网盘分享制作)")
    print("=" * 65)

    # 1. SenseVoice (本地已内置，直接取)
    print("\n[1/8] 准备 SenseVoice INT8 (中/英/日/韩/粤 多语种极速打字机)...")
    sv_src = "app/src/main/assets/models/sensevoice"
    sv_files = {
        "model.int8.onnx": os.path.join(sv_src, "model.int8.onnx"),
        "tokens.txt": os.path.join(sv_src, "tokens.txt"),
    }
    package_zip("01_SenseVoice_中英日韩粤多语种_INT8_毫秒级打字机(推荐).zip", sv_files)

    # 2. Paraformer 中文
    print("\n[2/8] 下载 Paraformer 中文 (阿里工业级会议长语音标杆)...")
    para_dir = os.path.join(CACHE_DIR, "paraformer")
    os.makedirs(para_dir, exist_ok=True)
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-2024-03-09/resolve/main/model.int8.onnx",
        os.path.join(para_dir, "model.int8.onnx"),
        desc="Paraformer model.int8.onnx (~216MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-2024-03-09/resolve/main/tokens.txt",
        os.path.join(para_dir, "tokens.txt"),
        desc="Paraformer tokens.txt"
    )
    package_zip("02_Paraformer_中文工业级会议_INT8(推荐).zip", {
        "model.int8.onnx": os.path.join(para_dir, "model.int8.onnx"),
        "tokens.txt": os.path.join(para_dir, "tokens.txt"),
    })

    # 3. Whisper Tiny
    print("\n[3/8] 下载 Whisper Tiny (OpenAI 99语种全能极速轻量版)...")
    wt_dir = os.path.join(CACHE_DIR, "whisper_tiny")
    os.makedirs(wt_dir, exist_ok=True)
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-encoder.int8.onnx",
        os.path.join(wt_dir, "tiny-encoder.int8.onnx"),
        desc="Whisper Tiny Encoder (~12MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-decoder.int8.onnx",
        os.path.join(wt_dir, "tiny-decoder.int8.onnx"),
        desc="Whisper Tiny Decoder (~85MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main/tiny-tokens.txt",
        os.path.join(wt_dir, "tokens.txt"),
        desc="Whisper Tiny Tokens"
    )
    package_zip("03_Whisper_Tiny_99语种极速轻量_INT8(推荐).zip", {
        "tiny-encoder.int8.onnx": os.path.join(wt_dir, "tiny-encoder.int8.onnx"),
        "tiny-decoder.int8.onnx": os.path.join(wt_dir, "tiny-decoder.int8.onnx"),
        "tokens.txt": os.path.join(wt_dir, "tokens.txt"),
    })

    # 4. Moonshine Tiny (英文超低延迟)
    print("\n[4/8] 下载 Moonshine Tiny (英文端侧超低延迟)...")
    moon_dir = os.path.join(CACHE_DIR, "moonshine_tiny")
    os.makedirs(moon_dir, exist_ok=True)
    moon_files = ["preprocess.onnx", "encode.int8.onnx", "uncached_decode.int8.onnx", "cached_decode.int8.onnx", "tokens.txt"]
    for mf in moon_files:
        download_file(
            f"https://hf-mirror.com/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8/resolve/main/{mf}",
            os.path.join(moon_dir, mf),
            desc=f"Moonshine {mf}"
        )
    package_zip("04_Moonshine_Tiny_英文边缘低延迟_INT8.zip", {
        mf: os.path.join(moon_dir, mf) for mf in moon_files
    })

    # 5. Whisper Small (高精版)
    print("\n[5/8] 下载 Whisper Small (OpenAI 99语种全能高精版)...")
    ws_dir = os.path.join(CACHE_DIR, "whisper_small")
    os.makedirs(ws_dir, exist_ok=True)
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-encoder.int8.onnx",
        os.path.join(ws_dir, "small-encoder.int8.onnx"),
        desc="Whisper Small Encoder (~112MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-decoder.int8.onnx",
        os.path.join(ws_dir, "small-decoder.int8.onnx"),
        desc="Whisper Small Decoder (~262MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small/resolve/main/small-tokens.txt",
        os.path.join(ws_dir, "tokens.txt"),
        desc="Whisper Small Tokens"
    )
    package_zip("05_Whisper_Small_99语种高精版_INT8.zip", {
        "small-encoder.int8.onnx": os.path.join(ws_dir, "small-encoder.int8.onnx"),
        "small-decoder.int8.onnx": os.path.join(ws_dir, "small-decoder.int8.onnx"),
        "tokens.txt": os.path.join(ws_dir, "tokens.txt"),
    })

    # 6. Whisper Turbo (旗舰极速版)
    print("\n[6/8] 下载 Whisper Large-v3-Turbo (OpenAI 旗舰极速版)...")
    wturbo_dir = os.path.join(CACHE_DIR, "whisper_turbo")
    os.makedirs(wturbo_dir, exist_ok=True)
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-encoder.int8.onnx",
        os.path.join(wturbo_dir, "turbo-encoder.int8.onnx"),
        desc="Whisper Turbo Encoder (~674MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-decoder.int8.onnx",
        os.path.join(wturbo_dir, "turbo-decoder.int8.onnx"),
        desc="Whisper Turbo Decoder (~361MB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main/turbo-tokens.txt",
        os.path.join(wturbo_dir, "tokens.txt"),
        desc="Whisper Turbo Tokens"
    )
    package_zip("06_Whisper_Turbo_旗舰极速版_INT8.zip", {
        "turbo-encoder.int8.onnx": os.path.join(wturbo_dir, "turbo-encoder.int8.onnx"),
        "turbo-decoder.int8.onnx": os.path.join(wturbo_dir, "turbo-decoder.int8.onnx"),
        "tokens.txt": os.path.join(wturbo_dir, "tokens.txt"),
    })

    # 7. FireRedASR (小红书工业级超低字错率)
    print("\n[7/8] 下载 FireRedASR (小红书工业级超低字错率)...")
    fire_dir = os.path.join(CACHE_DIR, "fire_red")
    os.makedirs(fire_dir, exist_ok=True)
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16/resolve/main/encoder.int8.onnx",
        os.path.join(fire_dir, "encoder.int8.onnx"),
        desc="FireRedASR Encoder (~1.1GB)"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16/resolve/main/decoder.int8.onnx",
        os.path.join(fire_dir, "decoder.int8.onnx"),
        desc="FireRedASR Decoder"
    )
    download_file(
        "https://hf-mirror.com/csukuangfj/sherpa-onnx-fire-red-asr-large-zh_en-2025-02-16/resolve/main/tokens.txt",
        os.path.join(fire_dir, "tokens.txt"),
        desc="FireRedASR Tokens"
    )
    package_zip("07_FireRedASR_小红书工业级中文低错率_INT8.zip", {
        "encoder.int8.onnx": os.path.join(fire_dir, "encoder.int8.onnx"),
        "decoder.int8.onnx": os.path.join(fire_dir, "decoder.int8.onnx"),
        "tokens.txt": os.path.join(fire_dir, "tokens.txt"),
    })

    # 8. Qwen3-ASR (阿里千问 0.6B)
    print("\n[8/8] 下载 Qwen3-ASR 0.6B (阿里千问新一代语音大模型)...")
    qwen_tar = os.path.join(CACHE_DIR, "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2")
    download_file(
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2",
        qwen_tar,
        use_proxy=True,
        desc="Qwen3-ASR 0.6B tar.bz2 (~837MB)"
    )
    if os.path.exists(qwen_tar):
        dest_qwen_zip = os.path.join(OUTPUT_DIR, "08_Qwen3_ASR_0.6B_阿里千问多语种方言_INT8.tar.bz2")
        if not os.path.exists(dest_qwen_zip):
            import shutil
            shutil.copy2(qwen_tar, dest_qwen_zip)
            print(f"  [就绪] 08_Qwen3_ASR_0.6B_阿里千问多语种方言_INT8.tar.bz2 ({os.path.getsize(dest_qwen_zip)/1024/1024:.1f} MB)")

    # 生成网盘配套说明文档
    readme_path = os.path.join(OUTPUT_DIR, "【使用必读】手机端怎么导入使用.txt")
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write("""====================================================================
 📱 实时翻译 (LiveTranslate) - 端侧开源语音识别模型网盘专用包
====================================================================

【模型选型建议】
1. 01_SenseVoice（强烈推荐 · 毫秒级）：
   - 特点：手机端测毫秒级极速出字，支持中、英、日、韩、粤多语种混说；
   - 提示：App 内已默认内置该模型，本包仅供需要恢复默认模型时使用。

2. 02_Paraformer（中文强烈推荐 · 极稳）：
   - 特点：阿里达摩院工业级普通话模型，会议演讲长句识别极稳，不漏字，断句标点规整。

3. 03_Whisper_Tiny（多语种推荐 · 极轻）：
   - 特点：仅 ~75MB，支持全球 99 种主流语言，超低 CPU 占用与低发热。

4. 05_Whisper_Turbo（旗舰高精版）：
   - 特点：OpenAI 旗舰级语音识别极速版，多语种泛化能力极强。

--------------------------------------------------------------------
【极简手机导入方法（免电脑 · 免权限）】
1. 把下载好的模型压缩包（.zip 或 .tar.bz2）保存到手机（比如存入手机“下载/Download”文件夹，或微信/QQ保存文件）；
2. 打开手机《实时翻译》App -> 点击「设置」 -> 点击「语音识别 (ASR)」；
3. 点击选中的模型卡片，找到并点击：
   👉 【📦 一键导入模型压缩包 (.zip / .tar.bz2 / .tar.gz)】
4. 在弹出的系统文件管理器中，直接选中刚才下载的模型包即可！
5. App 将在后台全自动解包、校验完整性并部署入库，状态立刻变为 🟢 已就绪 并直接可用！

--------------------------------------------------------------------
【传统 USB 拷贝方法（备用）】
若使用电脑连接手机拷贝，请将压缩包解压后的文件夹放入手机内部存储：
/Android/data/com.xzm.realtimetranslate/files/models/asr/<对应模型ID>/
====================================================================
""")

    print("\n" + "=" * 65)
    print(" 🎉 全部端侧 ASR 模型已准备完毕！")
    print(f" 📂 存放目录: {OUTPUT_DIR}")
    print("=" * 65)

if __name__ == "__main__":
    main()
