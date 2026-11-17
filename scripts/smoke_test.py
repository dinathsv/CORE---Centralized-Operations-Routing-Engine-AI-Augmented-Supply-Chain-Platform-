#!/usr/bin/env python3
"""Exercise a running stack through its frontend proxy (or gateway).

Creates a uniquely named item and order; removes the item and cancels the order.
The cancelled order remains as an audit record. Requires no third-party packages.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:3000")
    args = parser.parse_args()
    token = base64.b64encode(
        f"{os.getenv('CORE_ADMIN_USERNAME', 'admin')}:{os.getenv('CORE_ADMIN_PASSWORD', 'admin')}".encode()
    ).decode()

    def call(method: str, path: str, data=None, expected=200, auth=True):
        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = f"Basic {token}"
        request = Request(
            args.base_url.rstrip("/") + path,
            data=json.dumps(data).encode() if data is not None else None,
            headers=headers,
            method=method,
        )
        try:
            response = urlopen(request, timeout=90)
        except HTTPError as error:
            response = error
        with response:
            body = response.read().decode()
            assert response.status == expected, f"{method} {path}: expected {expected}, got {response.status}: {body}"
            return json.loads(body) if body else None

    call("GET", "/api/inventory", expected=401, auth=False)
    call("GET", "/api/orders", expected=401, auth=False)
    suffix = uuid.uuid4().hex[:12]
    item_id = order_id = None
    try:
        item = call("POST", "/api/inventory", {
            "sku": f"SMOKE-{suffix}", "name": "Smoke test item", "description": "Temporary verification item",
            "quantity": 8, "price": 12.50,
        }, expected=201)
        item_id = item["id"]
        call("POST", "/api/inventory", {
            "sku": item["sku"], "name": item["name"], "quantity": 1, "price": 1,
        }, expected=409)
        item["quantity"] = 10
        call("PUT", f"/api/inventory/{item_id}", item)
        assert call("GET", f"/api/inventory/{item_id}")["quantity"] == 10
        order = call("POST", "/api/orders", {
            "customerName": "Smoke test customer", "sku": item["sku"], "quantity": 2,
        }, expected=201)
        order_id = order["id"]
        assert order["status"] == "PENDING"
        call("PATCH", f"/api/orders/{order_id}/status?status=DELIVERED", expected=409)
        assert call("PATCH", f"/api/orders/{order_id}/status?status=CONFIRMED")["status"] == "CONFIRMED"
        collection = f"smoke-{suffix}"
        call("POST", "/api/ai/documents", {
            "collection": collection,
            "documents": [{"id": "returns", "text": "The returns policy allows unopened items to be returned within 30 days.", "source": "Smoke test policy"}],
        })
        answer = call("POST", "/api/ai/query", {"collection": collection, "query": "What is the returns policy?"})
        assert answer["answer"] and "Smoke test policy" in answer["sources"], answer
    finally:
        try:
            if order_id is not None:
                call("DELETE", f"/api/orders/{order_id}", expected=204)
        finally:
            if item_id is not None:
                call("DELETE", f"/api/inventory/{item_id}", expected=204)
                call("GET", f"/api/inventory/{item_id}", expected=404)
    print("PASS: authentication, inventory CRUD, duplicate SKU, order lifecycle, document ingestion and retrieval")


if __name__ == "__main__":
    main()
