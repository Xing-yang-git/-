# 基础镜像走 Docker Hub 官方名，由 daemon.json 的 registry-mirrors 重定向到国内镜像源拉取
FROM python:3.11-slim

WORKDIR /app

# torch + 依赖从主机预下载的 wheel 本地安装：
# 容器内网络仅 ~0.4MB/s（直拉 CUDA torch 821MB 需数小时），主机华为云 10.5MB/s 已预下到 wheels/（torch 2.7.1+cu126）
COPY wheels /wheels
RUN pip install --no-cache-dir --no-index --find-links /wheels torch==2.7.1 && rm -rf /wheels

# transformers + 服务依赖走阿里云 PyPI 镜像（容器内实测可达；包小，慢但量少）
RUN pip install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple \
    transformers fastapi uvicorn

# 模型从 HuggingFace 镜像站下载，缓存卷持久化避免重启重下
ENV HF_ENDPOINT=https://hf-mirror.com
ENV HF_HOME=/root/.cache/huggingface

COPY app.py /app/app.py

EXPOSE 8001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
