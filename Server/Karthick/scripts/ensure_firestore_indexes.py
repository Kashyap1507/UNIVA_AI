"""
Create Firestore composite indexes in wizly-ai-2026 (safe to re-run)

Sources:
    - firestore.indexes.json
    - "create_composite=..." links from "The query requires an index" errors,
      passed as arguments or pasted into a text file

Usage:
    python scripts/ensure_firestore_indexes.py
    python scripts/ensure_firestore_indexes.py "<index link from error>"
    python scripts/ensure_firestore_indexes.py links.txt
"""

import base64
import json
import re
import sys
import urllib.parse
from pathlib import Path

from google.api_core.exceptions import AlreadyExists
from google.cloud.firestore_admin_v1 import FirestoreAdminClient
from google.cloud.firestore_admin_v1.types import Index
from google.oauth2 import service_account

SERVER_DIR = Path(__file__).resolve().parent.parent
SERVICE_ACCOUNT = SERVER_DIR / "firebase-service-account.json"
PROJECT = "wizly-ai-2026"
INDEX_FILES = [SERVER_DIR / "firestore.indexes.json"]

# Indexes found missing by running the app (not in the JSON files)
KNOWN_LINKS = [
    # Console "create_composite=" links for indexes that are not in firestore.indexes.json
]

ORDER = {"ASCENDING": 1, "DESCENDING": 2}


def from_json(path):
    for spec in json.loads(path.read_text()).get("indexes", []):
        fields = []
        for f in spec["fields"]:
            if "order" in f:
                fields.append({"field_path": f["fieldPath"], "order": ORDER[f["order"]]})
            elif "vectorConfig" in f:
                fields.append(
                    {
                        "field_path": f["fieldPath"],
                        "vector_config": {"dimension": int(f["vectorConfig"]["dimension"]), "flat": {}},
                    }
                )
            else:
                fields.append({"field_path": f["fieldPath"], "array_config": 1})
        scope = 1 if spec.get("queryScope", "COLLECTION") == "COLLECTION" else 2
        yield spec["collectionGroup"], Index(query_scope=scope, fields=fields)


def from_links(text):
    for match in re.finditer(r"create_composite=([A-Za-z0-9_\-%=+/]+)", text):
        raw = urllib.parse.unquote(match.group(1))
        raw += "=" * (-len(raw) % 4)
        index = Index.deserialize(base64.urlsafe_b64decode(raw.replace("+", "-").replace("/", "_")))
        if f"projects/{PROJECT}/" not in index.name:
            sys.exit(f"Refusing index for another project: {index.name}")
        group = index.name.split("/collectionGroups/")[1].split("/")[0]
        index.name = ""
        yield group, index


def main():
    creds = service_account.Credentials.from_service_account_file(str(SERVICE_ACCOUNT))
    if creds.project_id != PROJECT:
        sys.exit(f"Service account is for {creds.project_id}, expected {PROJECT}")
    client = FirestoreAdminClient(credentials=creds)

    items = []
    for path in INDEX_FILES:
        items += list(from_json(path))
    items += list(from_links("\n".join(KNOWN_LINKS)))
    for arg in sys.argv[1:]:
        text = Path(arg).read_text() if Path(arg).is_file() else arg
        items += list(from_links(text))

    for group, index in items:
        parent = f"projects/{PROJECT}/databases/(default)/collectionGroups/{group}"
        fields = [
            (
                f.field_path,
                Index.IndexField.Order(f.order).name
                if f.order
                else f"VECTOR({f.vector_config.dimension})"
                if f.vector_config.dimension
                else "ARRAY",
            )
            for f in index.fields
            if f.field_path != "__name__"
        ]
        try:
            client.create_index(parent=parent, index=index)
            print(f"creating  {group} {fields}")
        except AlreadyExists:
            print(f"exists    {group} {fields}")

    print("\nIndexes take a few minutes to build: Firebase console -> Firestore -> Indexes")


if __name__ == "__main__":
    main()
