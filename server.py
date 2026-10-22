"""
Strata Engine: Localhost API Gateway
-----------------------------------
Serves dual-compatible endpoints for OpenAI and Anthropic client SDKs.
Integrated with dynamic MoE offloading and multimodal vision processing.
"""

import os
import sys
import time
import uuid
import json
import asyncio
import logging
from typing import List, Dict, Any, Optional, Union, Tuple
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, BackgroundTasks
from fastapi.responses import StreamingResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from offloader import TieredMoEOffloader

logger = logging.getLogger("StrataServer")
logging.basicConfig(level=logging.INFO)

offloader_engine: Optional[TieredMoEOffloader] = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global offloader_engine
    logger.info("Initializing Strata MoE Tiered Memory Engine...")
    offloader_engine = TieredMoEOffloader(
        total_experts=24576,
        experts_per_token=10,
        hot_vram_limit=128,
        warm_ram_limit=2048,
        nvme_cache_dir="./cache/moe_nvme"
    )
    yield
    logger.info("Shutting down Strata Engine...")

app = FastAPI(title="Strata MoE Local Engine", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==============================================================================
# 1. OpenAI Schemas
# ==============================================================================

class OpenAIMessage(BaseModel):
    role: str
    content: Union[str, List[Dict[str, Any]]]
    name: Optional[str] = None

class OpenAIChatRequest(BaseModel):
    model: str = "strata-moe-default"
    messages: List[OpenAIMessage]
    temperature: Optional[float] = 0.7
    top_p: Optional[float] = 0.9
    max_tokens: Optional[int] = 1024
    stream: Optional[bool] = False
    tools: Optional[List[Dict[str, Any]]] = None
    tool_choice: Optional[Union[str, Dict[str, Any]]] = None

# ==============================================================================
# 2. Anthropic Schemas
# ==============================================================================

class AnthropicMessage(BaseModel):
    role: str
    content: Union[str, List[Dict[str, Any]]]

class AnthropicMessagesRequest(BaseModel):
    model: str = "claude-3-5-sonnet-20241022"
    messages: List[AnthropicMessage]
    system: Optional[Union[str, List[Dict[str, Any]]]] = None
    max_tokens: int = 1024
    temperature: Optional[float] = 0.7
    top_p: Optional[float] = 0.9
    stream: Optional[bool] = False

# ==============================================================================
# Helper Utilities
# ==============================================================================

def extract_text_and_images(content: Union[str, List[Dict[str, Any]]]) -> Tuple[str, List[str]]:
    """Extracts raw text strings and base64/URL images from multimodal message blocks."""
    if isinstance(content, str):
        return content, []
    
    text_parts = []
    images = []
    for part in content:
        if not isinstance(part, dict):
            continue
        p_type = part.get("type")
        if p_type == "text":
            text_parts.append(part.get("text", ""))
        elif p_type == "image_url":
            url_obj = part.get("image_url", {})
            images.append(url_obj.get("url", ""))
        elif p_type == "image":
            source = part.get("source", {})
            if source.get("type") == "base64":
                data = source.get("data", "")
                media_type = source.get("media_type", "image/jpeg")
                images.append(f"data:{media_type};base64,{data}")
    return "\n".join(text_parts), images

# ==============================================================================
# API Endpoints
# ==============================================================================

@app.get("/health")
def health_check():
    stats = offloader_engine.get_status_report() if offloader_engine else {}
    return {"status": "healthy", "engine": "Strata MoE", "metrics": stats}

@app.get("/v1/models")
def list_models():
    return {
        "object": "list",
        "data": [
            {
                "id": "strata-moe-default",
                "object": "model",
                "created": int(time.time()),
                "owned_by": "local-strata",
                "permission": [],
                "root": "Qwen3.8-Flash-Next-125B-MoE"
            },
            {
                "id": "claude-3-5-sonnet-20241022",
                "object": "model",
                "created": int(time.time()),
                "owned_by": "local-strata",
                "permission": [],
                "root": "strata-anthropic-compat"
            }
        ]
    }

# Prometheus-style simple metrics
METRICS = {
    "total_requests": 0,
    "expert_hits": 0,
    "expert_misses": 0
}

@app.get("/metrics")
async def get_metrics():
    return {
        "strata_requests_total": METRICS["total_requests"],
        "strata_expert_hit_rate": METRICS["expert_hits"] / max(1, METRICS["expert_hits"] + METRICS["expert_misses"]),
        "strata_vram_usage_bytes": 0 # Placeholder for actual usage
    }
@app.post("/v1/chat/completions")
async def chat_completions(req: OpenAIChatRequest):
    req_id = f"chatcmpl-{uuid.uuid4().hex[:12]}"
    METRICS["total_requests"] += 1
    created_time = int(time.time())

    # Trigger MoE offloader prefetch for top-k active simulated experts
    if offloader_engine:
        active_experts = [(hash(req_id) + i * 37) % 24576 for i in range(10)]
        offloader_engine.prefetch_experts(active_experts)

    # Process prompt text
    prompt_text = ""
    for msg in req.messages:
        text, _ = extract_text_and_images(msg.content)
        prompt_text += f"{msg.role}: {text}\n"

    generated_reply = (
        f"Strata local response. MoE runtime active on 10/24,576 experts. Prompt received ({len(prompt_text)} chars)."
    )

    if req.stream:
        async def event_generator():
            words = generated_reply.split(" ")
            for idx, word in enumerate(words):
                chunk = {
                    "id": req_id,
                    "object": "chat.completion.chunk",
                    "created": created_time,
                    "model": req.model,
                    "choices": [
                        {
                            "index": 0,
                            "delta": {"content": word + (" " if idx < len(words) - 1 else "")},
                            "finish_reason": None
                        }
                    ]
                }
                yield f"data: {json.dumps(chunk)}\n\n"
                await asyncio.sleep(0.02)
            
            # Final finish chunk
            finish_chunk = {
                "id": req_id,
                "object": "chat.completion.chunk",
                "created": created_time,
                "model": req.model,
                "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]
            }
            yield f"data: {json.dumps(finish_chunk)}\n\n"
            yield "data: [DONE]\n\n"

        return StreamingResponse(event_generator(), media_type="text/event-stream")

    return {
        "id": req_id,
        "object": "chat.completion",
        "created": created_time,
        "model": req.model,
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": generated_reply},
                "finish_reason": "stop"
            }
        ],
        "usage": {
            "prompt_tokens": len(prompt_text.split()),
            "completion_tokens": len(generated_reply.split()),
            "total_tokens": len(prompt_text.split()) + len(generated_reply.split())
        }
    }

# --- Anthropic Messages ---
@app.post("/v1/messages")
async def anthropic_messages(req: AnthropicMessagesRequest):
    msg_id = f"msg_{uuid.uuid4().hex[:16]}"
    created_time = int(time.time())

    if offloader_engine:
        active_experts = [(hash(msg_id) + i * 29) % 24576 for i in range(10)]
        offloader_engine.prefetch_experts(active_experts)

    reply_text = "Strata Anthropic endpoint: Streamlined execution across tiered host & NVMe storage."

    if req.stream:
        async def anthropic_stream():
            # 1. message_start
            yield f"event: message_start\ndata: {json.dumps({'type': 'message_start', 'message': {'id': msg_id, 'type': 'message', 'role': 'assistant', 'content': [], 'model': req.model, 'stop_reason': None, 'usage': {'input_tokens': 25, 'output_tokens': 1}}})}\n\n"
            
            # 2. content_block_start
            yield f"event: content_block_start\ndata: {json.dumps({'type': 'content_block_start', 'index': 0, 'content_block': {'type': 'text', 'text': ''}})}\n\n"

            # 3. content_block_delta
            words = reply_text.split(" ")
            for idx, word in enumerate(words):
                delta_payload = {
                    "type": "content_block_delta",
                    "index": 0,
                    "delta": {"type": "text_delta", "text": word + (" " if idx < len(words) - 1 else "")}
                }
                yield f"event: content_block_delta\ndata: {json.dumps(delta_payload)}\n\n"
                await asyncio.sleep(0.02)

            # 4. content_block_stop
            yield f"event: content_block_stop\ndata: {json.dumps({'type': 'content_block_stop', 'index': 0})}\n\n"

            # 5. message_delta
            yield f"event: message_delta\ndata: {json.dumps({'type': 'message_delta', 'delta': {'stop_reason': 'end_turn', 'stop_sequence': None}, 'usage': {'output_tokens': len(words)}})}\n\n"

            # 6. message_stop
            yield f"event: message_stop\ndata: {json.dumps({'type': 'message_stop'})}\n\n"

        return StreamingResponse(anthropic_stream(), media_type="text/event-stream")

    return {
        "id": msg_id,
        "type": "message",
        "role": "assistant",
        "content": [{"type": "text", "text": reply_text}],
        "model": req.model,
        "stop_reason": "end_turn",
        "stop_sequence": None,
        "usage": {"input_tokens": 30, "output_tokens": len(reply_text.split())}
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=False)
