#!/usr/bin/env python3
"""Temporarily stop worker 1 and verify an uncached request still succeeds."""

import json
import subprocess
import time
import urllib.request
import uuid


def compose(*args):
    subprocess.run(["docker", "compose", *args], check=True)


def worker_is_down():
    with urllib.request.urlopen("http://localhost:8080/actuator/prometheus", timeout=5) as response:
        return 'inference_worker_healthy{worker="inference-worker-1"} 0.0' in response.read().decode()


def main():
    compose("stop", "inference-worker-1")
    try:
        deadline = time.monotonic() + 20
        while not worker_is_down():
            if time.monotonic() >= deadline:
                raise TimeoutError("gateway did not mark worker 1 unhealthy")
            time.sleep(1)

        request = urllib.request.Request(
            "http://localhost:8080/api/v1/inference",
            data=json.dumps({
                "model": "mock-llm", "prompt": f"failover {uuid.uuid4()}",
                "temperature": 0.7, "maxTokens": 40,
            }).encode(),
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            result = json.load(response)
        assert result["cached"] is False
        assert result["workerId"] in {"worker-2", "worker-3"}
        print(f"Failover passed: request ran on {result['workerId']}")
    finally:
        compose("start", "inference-worker-1")


if __name__ == "__main__":
    main()
