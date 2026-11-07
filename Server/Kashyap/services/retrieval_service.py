"""
Course-scoped retrieval over material_chunks with Firestore vector search.

Every query is pre-filtered on user_id AND course_id, so a student can only
ever retrieve text from their own course. Needs the composite vector index
(user_id, course_id, embedding 768 COSINE) from MIGRATION_PLAN §1.
"""

import logging
from dataclasses import dataclass
from typing import Callable, List, Optional

from google.cloud.firestore_v1.base_query import FieldFilter
from google.cloud.firestore_v1.base_vector_query import DistanceMeasure
from google.cloud.firestore_v1.vector import Vector

from app.models.course import MATERIAL_CHUNKS

logger = logging.getLogger(__name__)

DEFAULT_K = 8
MAX_K = 20
MAX_QUERY_CHARS = 2000


@dataclass
class RetrievedChunk:
    chunk_id: str
    material_id: str
    material_title: str
    text: str
    page: Optional[int]
    page_end: Optional[int]
    distance: Optional[float]

    def to_response(self) -> dict:
        return {
            "chunk_id": self.chunk_id,
            "material_id": self.material_id,
            "material_title": self.material_title,
            "text": self.text,
            "page": self.page,
            "page_end": self.page_end,
            "score": None if self.distance is None else round(1 - self.distance, 4),
        }


def _default_embed_query(query: str) -> List[float]:
    from app.ai_framework.utils.embedding_client import EmbeddingClientFactory

    return EmbeddingClientFactory.get_client().embed_query(query)


def search(
    user_id: str,
    course_id: str,
    query: str,
    k: int = DEFAULT_K,
    material_ids: Optional[List[str]] = None,
    embed_query: Optional[Callable[[str], List[float]]] = None,
    db=None,
) -> List[RetrievedChunk]:
    """Top-k chunks for `query` within one course (caller checks course ownership)."""
    if not user_id or not course_id:
        raise ValueError("user_id and course_id are required")
    query = (query or "").strip()[:MAX_QUERY_CHARS]
    if not query:
        return []
    k = max(1, min(int(k), MAX_K))
    embed_query = embed_query or _default_embed_query

    if db is None:
        from app.services.firebase_service import get_firestore_client

        db = get_firestore_client()

    base = (
        db.collection(MATERIAL_CHUNKS)
        .where(filter=FieldFilter("user_id", "==", user_id))
        .where(filter=FieldFilter("course_id", "==", course_id))
    )
    vector_query = base.find_nearest(
        vector_field="embedding",
        query_vector=Vector(embed_query(query)),
        distance_measure=DistanceMeasure.COSINE,
        limit=k if not material_ids else MAX_K,
        distance_result_field="vector_distance",
    )

    results = []
    for doc in vector_query.stream():
        data = doc.to_dict() or {}
        # Defence in depth: never return another user's or course's text
        if data.get("user_id") != user_id or data.get("course_id") != course_id:
            continue
        if material_ids and data.get("material_id") not in material_ids:
            continue
        results.append(
            RetrievedChunk(
                chunk_id=doc.id,
                material_id=data.get("material_id", ""),
                material_title=data.get("material_title", ""),
                text=data.get("text", ""),
                page=data.get("page"),
                page_end=data.get("page_end"),
                distance=data.get("vector_distance"),
            )
        )
    return results[:k]
