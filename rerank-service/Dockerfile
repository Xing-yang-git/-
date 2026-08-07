# 基础镜像走 Docker Hub 官方名，由 daemon.json 的 registry-mirrors 重定向到国内镜像源拉取
FROM python:3.11-slim

WORKDIR /app

# 先装 CPU 版 torch（避免从 PyPI 拉 CUDA 那几个 GB），PyTorch 官方 CPU 源
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

# transformers + 服务依赖走腾讯 PyPI 镜像（直接加载 bge-reranker-v2-m3，不用 FlagEmbedding 重链）
RUN pip install --no-cache-dir -i https://mirrors.cloud.tencent.com/pypi/simple \
    transformers fastapi uvicorn

# 模型从 HuggingFace 镜像站下载，缓存卷持久化避免重启重下
ENV HF_ENDPOINT=https://hf-mirror.com
ENV HF_HOME=/root/.cache/huggingface

COPY app.py /app/app.py

EXPOSE 8001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
