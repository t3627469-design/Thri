from fastapi import FastAPI
from .loader import load_prompt

app = FastAPI(title="REX API")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/prompt")
def get_prompt():
    return {"prompt": load_prompt()}
