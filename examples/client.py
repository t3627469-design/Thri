import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import anthropic
from src.loader import load_prompt


def main():
    client = anthropic.Anthropic()
    system_prompt = load_prompt()

    message = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=1024,
        system=system_prompt,
        messages=[{"role": "user", "content": "hey. what're we building?"}],
    )

    print(message.content[0].text)


if __name__ == "__main__":
    main()
