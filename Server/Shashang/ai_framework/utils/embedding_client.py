"""
Google Embedding Client for RAG

Uses the google-genai SDK. The model and vector size are configurable:

    EMBEDDING_MODEL       default "gemini-embedding-001"
    EMBEDDING_DIMENSION   default 768 (must match the Firestore vector index)

text-embedding-004 (the previous default) has been retired upstream.
Documents and queries are embedded with the matching retrieval task types.
Includes LangSmith tracing for observability.
"""

import logging
import os
from typing import List, Optional

from google import genai
from google.genai import types

from app.utils import usage_meter

# LangSmith tracing
try:
    from langsmith import traceable

    LANGSMITH_AVAILABLE = True
except ImportError:
    LANGSMITH_AVAILABLE = False

    # No-op decorator if langsmith not available
    def traceable(*args, **kwargs):
        def decorator(func):
            return func

        return decorator


DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001"
DEFAULT_EMBEDDING_DIMENSION = 768
MAX_BATCH_SIZE = 100  # API limit per embed_content request


def configured_embedding_model() -> str:
    return os.getenv("EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL)


def configured_embedding_dimension() -> int:
    return int(os.getenv("EMBEDDING_DIMENSION", str(DEFAULT_EMBEDDING_DIMENSION)))


class GoogleEmbeddingClient:
    """Text embeddings for RAG (documents and search queries)."""

    def __init__(
        self,
        model_name: Optional[str] = None,
        api_key: Optional[str] = None,
        dimension: Optional[int] = None,
    ):
        self.model_name = (model_name or configured_embedding_model()).removeprefix("models/")
        self.dimension = dimension or configured_embedding_dimension()
        self.logger = logging.getLogger(__name__)

        self.api_key = api_key or os.getenv("GOOGLE_GEMINI_API_KEY")
        if not self.api_key:
            raise ValueError(
                "Google Gemini API key not found. "
                "Set GOOGLE_GEMINI_API_KEY environment variable."
            )

        self.client = genai.Client(api_key=self.api_key)
        self.logger.info(
            f"Initialized embedding client: {self.model_name} ({self.dimension} dims)"
        )

    def _embed(self, texts: List[str], task_type: str) -> List[List[float]]:
        response = self.client.models.embed_content(
            model=self.model_name,
            contents=texts,
            config=types.EmbedContentConfig(
                task_type=task_type, output_dimensionality=self.dimension
            ),
        )
        # The API returns no token counts for embeddings: meter an estimate (~4 characters per token)
        usage_meter.add(self.model_name, sum(len(t) for t in texts) // 4, 0)
        vectors = [list(e.values) for e in response.embeddings]
        if len(vectors) != len(texts):
            raise RuntimeError(
                f"Embedding count mismatch: sent {len(texts)}, got {len(vectors)}"
            )
        return vectors

    @traceable(name="embed_text", run_type="embedding")
    def embed_text(self, text: str, task_type: str = "RETRIEVAL_DOCUMENT") -> List[float]:
        """Embed one piece of text."""
        if not text or not text.strip():
            raise ValueError("Text cannot be empty")
        return self._embed([text], task_type)[0]

    @traceable(name="embed_batch", run_type="embedding")
    def embed_batch(
        self,
        texts: List[str],
        batch_size: int = 50,
        task_type: str = "RETRIEVAL_DOCUMENT",
    ) -> List[List[float]]:
        """
        Embed many texts, in request-sized batches. Output order matches input.
        Empty texts are rejected so indexes stay aligned with the caller's list.
        """
        if not texts:
            return []
        if any(not t or not t.strip() for t in texts):
            raise ValueError("Cannot embed empty text")

        batch_size = max(1, min(batch_size, MAX_BATCH_SIZE))
        vectors: List[List[float]] = []
        for i in range(0, len(texts), batch_size):
            vectors.extend(self._embed(texts[i : i + batch_size], task_type))
        self.logger.info(f"Generated {len(vectors)} embeddings with {self.model_name}")
        return vectors

    def embed_query(self, query: str) -> List[float]:
        """Embed a search query (uses the query-side task type)."""
        return self.embed_text(query, task_type="RETRIEVAL_QUERY")

    def get_embedding_dimension(self) -> int:
        return self.dimension


class EmbeddingClientFactory:
    """Reuses one client per (model, key)."""

    _clients: dict[str, GoogleEmbeddingClient] = {}

    @classmethod
    def get_client(
        cls,
        model_name: Optional[str] = None,
        api_key: Optional[str] = None,
    ) -> GoogleEmbeddingClient:
        model = (model_name or configured_embedding_model()).removeprefix("models/")
        cache_key = f"{model}:{api_key or 'default'}"
        if cache_key not in cls._clients:
            cls._clients[cache_key] = GoogleEmbeddingClient(model_name=model, api_key=api_key)
        return cls._clients[cache_key]

    @classmethod
    def clear_cache(cls):
        cls._clients.clear()
