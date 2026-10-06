# REX

REX is a custom Claude persona — a creative and technical partner for fiction writing, code engineering, and general collaboration. It operates as a system-level prompt injected via Claude's API.

## Setup

```bash
pip install -r requirements.txt
```

## Run the API

```bash
uvicorn src.api:app --port 8000
```

Endpoints:

- `GET /health` — liveness check
- `GET /prompt` — returns the REX system prompt

## Use the example client

Set your Anthropic API key, then:

```bash
python examples/client.py
```

This sends a test message to Claude with the REX persona injected as the system prompt.

## Override the prompt path

```bash
REX_PROMPT_PATH=/path/to/custom.md python examples/client.py
```
