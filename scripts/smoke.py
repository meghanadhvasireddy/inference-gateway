#!/usr/bin/env python3
"""Check a running Compose stack without extra Python dependencies."""

import json
import os
import urllib.request
import uuid


BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")


def infer(prompt):
    payload = json.dumps({
        "model": "mock-llm",
        "prompt": prompt,
        "temperature": 0.7,
        "maxTokens": 40,
    }).encode()
    request = urllib.request.Request(
        f"{BASE_URL}/api/v1/inference",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
        assert response.headers["X-Request-Id"] == result["requestId"]
        return result


def main():
    with urllib.request.urlopen(f"{BASE_URL}/actuator/health/readiness", timeout=5) as response:
        assert json.load(response)["status"] == "UP"

    prompt = f"smoke check {uuid.uuid4()}"
    fresh = infer(prompt)
    cached = infer(prompt)
    assert fresh["cached"] is False
    assert cached["cached"] is True
    assert fresh["response"] == cached["response"]
    assert fresh["requestId"] != cached["requestId"]
    assert fresh["workerId"]

    with urllib.request.urlopen(f"{BASE_URL}/actuator/prometheus", timeout=5) as response:
        assert b"inference_requests_total" in response.read()

    print(f"Smoke check passed: worker={fresh['workerId']}, cache hit confirmed")


if __name__ == "__main__":
    main()
