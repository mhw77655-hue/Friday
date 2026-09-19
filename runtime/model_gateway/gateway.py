import json
import urllib.request
from typing import Any

MODEL_SERVER = "http://127.0.0.1:8080"


class ModelGatewayError(RuntimeError):
    pass


def _request(path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
    url = MODEL_SERVER + path

    if payload is None:
        request = urllib.request.Request(url, method="GET")
    else:
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )

    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise ModelGatewayError(f"Model request failed: {exc}") from exc


def health() -> bool:
    try:
        data = _request("/health")
        return data.get("status") == "ok"
    except Exception:
        return False


def active_model() -> str | None:
    data = _request("/v1/models")
    models = data.get("data", [])
    return models[0].get("id") if models else None


def chat(
    messages: list[dict[str, str]],
    *,
    max_tokens: int = 256,
    temperature: float = 0.2,
) -> dict[str, Any]:
    if not health():
        raise ModelGatewayError("Local model server is offline")

    payload = {
        "model": "jarvis-local",
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
    }

    return _request("/v1/chat/completions", payload)


def text(
    messages: list[dict[str, str]],
    *,
    max_tokens: int = 256,
    temperature: float = 0.2,
) -> str:
    data = chat(
        messages,
        max_tokens=max_tokens,
        temperature=temperature,
    )

    message = data["choices"][0]["message"]

    content = message.get("content")

    if isinstance(content, str) and content.strip():
        return content.strip()

    reasoning = message.get("reasoning_content")

    if isinstance(reasoning, str) and reasoning.strip():
        return reasoning.strip()

    return ""


if __name__ == "__main__":
    print("HEALTH:", health())
    print("MODEL:", active_model())

    print(
        "TEST:",
        repr(
            text(
                [
                    {
                        "role": "system",
                        "content": (
                            "Follow the user's instruction exactly. "
                            "Do not explain."
                        ),
                    },
                    {
                        "role": "user",
                        "content": (
                            "Reply with exactly: "
                            "JARVIS GATEWAY ONLINE"
                        ),
                    },
                ],
                max_tokens=20,
                temperature=0,
            )
        ),
    )
