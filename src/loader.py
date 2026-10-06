import os
from pathlib import Path


def load_prompt(path: str | None = None) -> str:
    if path is None:
        path = os.environ.get("REX_PROMPT_PATH")
    if path is None:
        path = Path(__file__).parent.parent / "prompts" / "rex.md"
    return Path(path).read_text(encoding="utf-8")
