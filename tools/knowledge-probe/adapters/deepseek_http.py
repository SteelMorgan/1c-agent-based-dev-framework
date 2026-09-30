"""
Адаптер DeepSeek через HTTP POST на /chat/completions.

Ключ читается (в порядке приоритета):
1. из переменной окружения DEEPSEEK_API_KEY;
2. из файла ~/.config/deepseek/api_key (одна строка с ключом, chmod 600).
Если ключа нет нигде — адаптер РЕГИСТРИРУЕТСЯ (импортируется без ошибок),
но при вызове ask() бросает понятную ошибку с подсказкой.
Ключ в файлы репозитория не пишем.

Endpoint и temperature — в config.py. Формат запроса — OpenAI-совместимый
(messages: system + user). Модельные id — см. config.MODELS (проверить по
https://api-docs.deepseek.com/).

Транспорт: используем requests, если установлен; иначе fallback на urllib
из stdlib — чтобы адаптер работал без внешних зависимостей.
"""
import json
import os

from . import AdapterError
from config import (
    DEEPSEEK_ENDPOINT,
    DEEPSEEK_TEMPERATURE,
    PROBE_SYSTEM,
)


def _post(url: str, headers: dict, payload: dict, timeout: int) -> dict:
    data = json.dumps(payload).encode("utf-8")
    # Пытаемся использовать requests, иначе stdlib urllib.
    try:
        import requests  # noqa: WPS433 (опциональная зависимость)
        try:
            resp = requests.post(url, headers=headers, data=data, timeout=timeout)
        except requests.exceptions.RequestException as e:
            # SSL EOF, обрывы соединения, таймауты — переводим в AdapterError,
            # иначе необработанное исключение убивает весь прогон.
            raise AdapterError(f"DeepSeek сетевая ошибка: {e}")
        if resp.status_code != 200:
            raise AdapterError(
                f"DeepSeek HTTP {resp.status_code}: {resp.text[:500]}"
            )
        return resp.json()
    except ImportError:
        import urllib.error
        import urllib.request
        req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", "replace")[:500]
            raise AdapterError(f"DeepSeek HTTP {e.code}: {body}")
        except urllib.error.URLError as e:
            raise AdapterError(f"DeepSeek сетевая ошибка: {e}")


KEY_FILE = os.path.expanduser("~/.config/deepseek/api_key")


def _get_api_key() -> str:
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if api_key:
        return api_key
    try:
        with open(KEY_FILE, encoding="utf-8") as f:
            api_key = f.read().strip()
    except OSError:
        api_key = ""
    if not api_key:
        raise AdapterError(
            "Ключ DeepSeek не найден. Либо export DEEPSEEK_API_KEY=<ключ>, "
            f"либо впишите ключ одной строкой в файл {KEY_FILE} (chmod 600)."
        )
    return api_key


def _run_once(model_id: str, prompt: str, timeout: int) -> str:
    api_key = _get_api_key()
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    # prompt здесь — полный текст вопроса (преамбула уже включена build_probe_prompt).
    # Дублируем системную роль отдельно для корректной OpenAI-совместимой схемы.
    payload = {
        "model": model_id,
        "messages": [
            {"role": "system", "content": PROBE_SYSTEM},
            {"role": "user", "content": prompt},
        ],
        "temperature": DEEPSEEK_TEMPERATURE,
        "stream": False,
    }
    body = _post(DEEPSEEK_ENDPOINT, headers, payload, timeout)
    try:
        out = body["choices"][0]["message"]["content"].strip()
    except (KeyError, IndexError, TypeError) as e:
        raise AdapterError(f"DeepSeek: неожиданный формат ответа: {e}; body={str(body)[:300]}")
    if not out:
        raise AdapterError("DeepSeek вернул пустой ответ")
    return out


def ask(model_id: str, prompt: str, timeout: int, retries: int) -> str:
    import time
    last = None
    for attempt in range(retries + 1):
        try:
            return _run_once(model_id, prompt, timeout)
        except AdapterError as e:
            last = e
            # Нет смысла ретраить отсутствие ключа
            if "DEEPSEEK_API_KEY" in str(e):
                break
            if attempt < retries:
                time.sleep(3 * (attempt + 1))  # пауза перед повтором сетевого сбоя
    raise last
