"""
title: Alfresco RAG Pipeline
authors: Kamil Kopciuch, Julian Helwig
date: 2025-04-24
version: 0.0
license: MIT
description: Alfresco RAG Pipeline
requirements: 
"""
from typing import List, Optional, Union, Generator, Iterator
from pydantic import BaseModel, Field
import requests
import json
import os
import logging
import time


class Pipeline:
    class Valves(BaseModel):
        RAG_API_URL: str = Field(
            default=os.getenv("RAG_API_URL", "http://beone-ai-stack-alfresco-ai-framework-1:9999/chat"),
            description="URL for accessing RAG API endpoints.",
        )
        RAG_STREAMING_API_URL: str = Field(
            default=os.getenv("RAG_STREAMING_API_URL", "http://beone-ai-stack-alfresco-ai-framework-1:9999/chat/stream"),
            description="URL for accessing RAG streaming API endpoints.",
        )
        DEBUG_LOGGING: bool

    def __init__(self):
        self.name = "Alfresco RAG Pipeline"
        self.valves = self.Valves(
            **{
                "RAG_API_URL": os.getenv("RAG_API_URL", "http://beone-ai-stack-alfresco-ai-framework-1:9999/chat"),
                "RAG_STREAMING_API_URL": os.getenv("RAG_STREAMING_API_URL", "http://beone-ai-stack-alfresco-ai-framework-1:9999/chat/stream"),
                "DEBUG_LOGGING": os.getenv("DEBUG_LOGGING", True),
            }
        )
        logging.basicConfig(
            level= logging.DEBUG if self.valves.DEBUG_LOGGING else logging.INFO,
            format="%(asctime)s - %(levelname)s - %(message)s",
            force=True
        )
        self.pipelines = []
        self.classifier = None
        logging.debug(f"init finished for: {__name__}, valves: {self.valves}")
        pass

    async def on_startup(self):
        logging.debug(f"on_startup:{__name__}")
        pass

    async def on_shutdown(self):
        logging.debug(f"on_shutdown:{__name__}")
        pass

    async def on_valves_updated(self):
        logging.debug(f"on_valves_updated:{__name__}")
        pass

    async def inlet(self, body: dict, user: Optional[dict] = None) -> dict:
        logging.debug(f"inlet:{__name__}")
        return body

    async def outlet(self, body: dict, user: Optional[dict] = None) -> dict:
        logging.debug(f"outlet:{__name__}")
        return body

    def stream_json_response(self, json_str, chunk_size=20):
        for i in range(0, len(json_str), chunk_size):
            yield json_str[i:i+chunk_size]

    def pipe(
            self, user_message: str, model_id: str, messages: List[dict], body: dict
    ) -> Union[str, Generator, Iterator]:
        start = time.time()
        logging.debug(f"pipe:{__name__}")
        if "user" in body:
            logging.debug("######################################")
            logging.debug(f'# User: {body["user"]["name"]} ({body["user"]["id"]})')
            logging.debug(f"# Message: {user_message}")
            logging.debug("######################################")

        if user_message.__contains__("Generate a concise, 3-5 word title with an emoji summarizing the chat history."):
            logging.debug(f'# Title Generation for pipe: {__name__}')
            return json.dumps({
                "title": "Alfresco RAG chat"
            })

        if user_message.__contains__("Generate 1-3 broad tags categorizing the main themes of the chat history"):
            logging.debug(f'# Tag Generation for pipe: {__name__}')
            return json.dumps({ "tags": ["alfresco"] })

        headers = {'Content-Type': 'text/plain; charset=utf-8'}
        rag_url = self.valves.RAG_STREAMING_API_URL if body["stream"] else self.valves.RAG_API_URL
        r = requests.post(
            url=f"{rag_url}",
            data=user_message,
            stream=True,
            headers=headers,
        )
        r.raise_for_status()
        try:
            if body["stream"]:
                return r.iter_content(chunk_size=512)
            else:
                response_data = r.json()
                logging.debug("######################################")
                logging.debug(f'# Assistant: {self.valves.OLLAMA_MODEL} ({self.valves.RAG_API_URL})')
                logging.debug(f"# Response: {response_data}")
                logging.debug(f"# Pipeline Time: {time.time() - start}")
                logging.debug("######################################")
                answer = response_data.get("answer", "")
                return answer
        except Exception as e:
            return f"Error: {e}"
