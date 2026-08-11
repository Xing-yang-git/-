"""bge-reranker-v2-m3 重排服务 — 独立 FastAPI 微服务。

社区互助平台 RAG 的知识检索重排器。用 transformers 直接加载 bge-reranker-v2-m3
（cross-encoder），评分逻辑与 FlagEmbedding.FlagReranker 一致：
query/passage 分别截断 → prepare_for_model 拼接 → 分类头 logits → sigmoid 归一。
不引入 FlagEmbedding 全家桶（其依赖 datasets/pyarrow 重链且国内镜像哈希不稳）。

接口契约（与后端 RerankerService 对齐）：
  POST /api/rerank
    { "query": "...", "documents": ["...", "..."], "model": "忽略" }
  → { "results": [ {"index": 0, "relevance_score": 0.98}, ... ] }
  index 是 documents 数组下标，results 已按 relevance_score 降序。
"""
import os
from typing import List, Optional

import numpy as np
import torch
from fastapi import FastAPI
from pydantic import BaseModel
from transformers import AutoModelForSequenceClassification, AutoTokenizer

app = FastAPI(title="bge-reranker-v2-m3 重排服务")

# 模型文件由部署脚本从 ModelScope 下载到本地目录（hf-mirror 不支持 HEAD 导致 huggingface_hub 在线校验失败），
# 直接用本地目录加载，绕开 HF 缓存机制；目录由 RERANK_MODEL_DIR 环境变量注入
MODEL_DIR = os.environ.get("RERANK_MODEL_DIR", "/models/bge-reranker-v2-m3")
# GPU 优先（容器经 docker GPU 透传可见 CUDA），无 GPU 自动回退 CPU；模型 fp32 权重约 2.2GB，6GB 显存可容纳
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR).to(device)
model.eval()

MAX_LENGTH = 512  # 与 FlagReranker 默认一致


def sigmoid(x):
    return float(1 / (1 + np.exp(-x)))


def compute_scores(query: str, passages: List[str]) -> List[float]:
    """对 query 与所有 passage 批量打分（单次前向），与 FlagReranker.compute_score(normalize=True) 等价。

    直接传 query+passage 两段文本让 tokenizer 拼接成 <s> query </s> passage </s>，
    与 FlagReranker 的截断拼接语义一致。批量推理显著快于逐条循环（GPU 上差距更大）。
    """
    with torch.no_grad():
        inputs = tokenizer(
            [(query, p) for p in passages],
            return_tensors="pt",
            truncation="only_second",
            max_length=MAX_LENGTH,
            padding=True,
        ).to(device)
        logits = model(**inputs).logits.view(-1).float()
    return [sigmoid(float(l)) for l in logits]


class RerankRequest(BaseModel):
    query: str
    documents: List[str]
    model: Optional[str] = None  # 兼容 Ollama 风格请求体，本服务忽略


class RerankResult(BaseModel):
    index: int
    relevance_score: float


class RerankResponse(BaseModel):
    results: List[RerankResult]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest):
    """对 query 与每个 document 打分，按相关性降序返回 index + score。"""
    scores = compute_scores(req.query, req.documents)
    results = [
        {"index": i, "relevance_score": score}
        for i, score in enumerate(scores)
    ]
    results.sort(key=lambda x: x["relevance_score"], reverse=True)
    return {"results": results}
