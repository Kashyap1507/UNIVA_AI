"""
Minimal in-memory Firestore stand-in for unit/API tests.

Supports collection().document(), get/set/update/delete, where (positional or
filter=FieldFilter), order_by, limit, stream, batch() and find_nearest
(cosine distance, computed exactly). Server timestamps are stored as-is.
set(merge=True) merges nested maps and applies Increment like Firestore does;
DELETE_FIELD removes a field. Document refs can list their subcollections.
"""

import copy
import itertools
import math
from datetime import datetime

from google.cloud.firestore import DELETE_FIELD, SERVER_TIMESTAMP
from google.cloud.firestore_v1 import Increment

_ids = itertools.count(1)

_OPS = {
    "==": lambda a, b: a == b,
    "!=": lambda a, b: a != b,
    "<": lambda a, b: a is not None and a < b,
    "<=": lambda a, b: a is not None and a <= b,
    ">": lambda a, b: a is not None and a > b,
    ">=": lambda a, b: a is not None and a >= b,
    "in": lambda a, b: a in b,
    "array_contains": lambda a, b: isinstance(a, list) and b in a,
}


def _get(data, field):
    """Read a field, following dotted paths into maps ("srs.due_at")."""
    for part in field.split("."):
        if not isinstance(data, dict):
            return None
        data = data.get(part)
    return data


def _resolve(data):
    """Replace SERVER_TIMESTAMP sentinels the way Firestore does on write."""
    return {k: (datetime.now() if v is SERVER_TIMESTAMP else v) for k, v in data.items()}


def _apply(existing, new):
    """Merge `new` into `existing` (nested maps merge, Increment adds, everything else replaces)."""
    out = dict(existing or {})
    for key, value in new.items():
        if value is DELETE_FIELD:
            out.pop(key, None)
        elif isinstance(value, Increment):
            out[key] = (out.get(key) or 0) + value._value
        elif isinstance(value, dict) and isinstance(out.get(key), dict):
            out[key] = _apply(out[key], value)
        elif isinstance(value, dict):
            out[key] = _apply({}, value)
        else:
            out[key] = copy.deepcopy(value)
    return out


class FakeSnapshot:
    def __init__(self, ref, data):
        self.reference = ref
        self.id = ref.id
        self._data = data

    @property
    def exists(self):
        return self._data is not None

    def to_dict(self):
        return copy.deepcopy(self._data) if self._data is not None else None


class FakeDocRef:
    def __init__(self, store, collection, doc_id):
        self._store = store
        self._collection = collection
        self.id = doc_id
        self.path = f"{collection}/{doc_id}"

    def _docs(self):
        return self._store.setdefault(self._collection, {})

    def get(self):
        return FakeSnapshot(self, copy.deepcopy(self._docs().get(self.id)))

    def set(self, data, merge=False):
        data = _resolve(data)
        if merge and self.id in self._docs():
            self._docs()[self.id] = _apply(self._docs()[self.id], data)
        else:
            self._docs()[self.id] = _apply({}, data)

    def update(self, data):
        if self.id not in self._docs():
            raise KeyError(f"No document to update: {self.path}")
        doc = self._docs()[self.id]
        for key, value in _resolve(data).items():
            if value is DELETE_FIELD:
                doc.pop(key, None)
            else:
                doc[key] = copy.deepcopy(value)

    def delete(self):
        self._docs().pop(self.id, None)

    def collection(self, name):
        return FakeQuery(self._store, f"{self.path}/{name}")

    def collections(self):
        prefix = f"{self.path}/"
        names = {key[len(prefix):] for key in self._store if key.startswith(prefix) and "/" not in key[len(prefix):]}
        return [FakeQuery(self._store, prefix + name) for name in sorted(names) if self._store[prefix + name]]


class FakeQuery:
    def __init__(self, store, collection, filters=None, order=None, limit_n=None):
        self._store = store
        self._collection = collection
        self._filters = filters or []
        self._order = order or []
        self._limit = limit_n

    @property
    def id(self):
        return self._collection.rsplit("/", 1)[-1]

    def _copy(self, **kw):
        q = FakeQuery(self._store, self._collection, list(self._filters), list(self._order), self._limit)
        for k, v in kw.items():
            setattr(q, k, v)
        return q

    def add(self, data):
        ref = self.document()
        ref.set(data)
        return None, ref

    def document(self, doc_id=None):
        return FakeDocRef(self._store, self._collection, doc_id or f"fake{next(_ids)}")

    def where(self, field=None, op=None, value=None, filter=None):
        if filter is not None:
            field, op, value = filter.field_path, filter.op_string, filter.value
        return self._copy(_filters=self._filters + [(field, op, value)])

    def order_by(self, field, direction="ASCENDING"):
        return self._copy(_order=self._order + [(field, direction)])

    def limit(self, n):
        return self._copy(_limit=n)

    def select(self, field_paths):
        return self._copy()

    def stream(self):
        docs = self._store.get(self._collection, {})
        rows = [
            (doc_id, data)
            for doc_id, data in docs.items()
            if all(_OPS[op](_get(data, field), value) for field, op, value in self._filters)
        ]
        for field, direction in reversed(self._order):
            rows.sort(key=lambda r: (_get(r[1], field) is None, str(_get(r[1], field))),
                      reverse=direction == "DESCENDING")
        if self._limit is not None:
            rows = rows[: self._limit]
        for doc_id, data in rows:
            yield FakeSnapshot(FakeDocRef(self._store, self._collection, doc_id), copy.deepcopy(data))

    def get(self):
        return list(self.stream())

    def find_nearest(self, vector_field, query_vector, limit, distance_measure, *, distance_result_field=None, distance_threshold=None):
        return FakeVectorQuery(self, vector_field, list(query_vector), limit, distance_result_field)


def _cosine_distance(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return 1.0 if not na or not nb else 1 - dot / (na * nb)


class FakeVectorQuery:
    def __init__(self, query, field, vector, limit, result_field):
        self._query, self._field, self._vector = query, field, vector
        self._limit, self._result_field = limit, result_field

    def stream(self):
        scored = []
        for snap in self._query.stream():
            data = snap.to_dict()
            stored = data.get(self._field)
            if stored is None:
                continue
            distance = _cosine_distance(list(stored), self._vector)
            if self._result_field:
                data[self._result_field] = distance
            scored.append((distance, FakeSnapshot(snap.reference, data)))
        scored.sort(key=lambda pair: pair[0])
        for _, snap in scored[: self._limit]:
            yield snap


class FakeBatch:
    def __init__(self):
        self._ops = []

    def set(self, ref, data, merge=False):
        self._ops.append(("set", ref, data, merge))

    def update(self, ref, data):
        self._ops.append(("update", ref, data, False))

    def delete(self, ref):
        self._ops.append(("delete", ref, None, False))

    def commit(self):
        for op, ref, data, merge in self._ops:
            if op == "set":
                ref.set(data, merge=merge)
            elif op == "update":
                ref.update(data)
            else:
                ref.delete()
        self._ops = []


class FakeFirestore:
    def __init__(self):
        self.store = {}

    def collection(self, name):
        return FakeQuery(self.store, name)

    def batch(self):
        return FakeBatch()

    def docs(self, collection):
        return self.store.get(collection, {})
