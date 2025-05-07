"""
title: Document Classification Pipeline
author: Julian Helwig, Michał Budzyński
date: 2025-04-16
version: 0.0
license: MIT
description: Document Classification Pipeline
requirements: transformers, langchain_core, torch, torchvision, torchaudio
"""
import json
import os
from typing import List, Optional, Union, Generator, Iterator
from pydantic import BaseModel
from transformers import pipeline
import logging
import time
from langchain_core.runnables import RunnableParallel
import torch

class Pipeline:
    class Valves(BaseModel):
        ZERO_SHOT_MODEL: str
        ZERO_SHOT_MODEL_SECONDARY: str
        USE_SECONDARY_MODEL: bool
        DEBUG_LOGGING: bool

    def __init__(self):
        self.name = "Document Classification Pipeline"
        self.valves = self.Valves(
            **{
                "DEBUG_LOGGING": os.getenv("DEBUG_LOGGING", True),
                # Example ZERO_SHOT_MODEL models:
                # - MoritzLaurer/mDeBERTa-v3-base-mnli-xnli (default)
                # - MoritzLaurer/deberta-v3-large-zeroshot-v1.1-all-33 (secondary)
                "ZERO_SHOT_MODEL": os.getenv("ZERO_SHOT_MODEL", "MoritzLaurer/mDeBERTa-v3-base-mnli-xnli"),
                "ZERO_SHOT_MODEL_SECONDARY": os.getenv("ZERO_SHOT_MODEL_SECONDARY", "MoritzLaurer/deberta-v3-large-zeroshot-v1.1-all-33"),
                "USE_SECONDARY_MODEL": os.getenv("USE_SECONDARY_MODEL", False),
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
        self.predownload_model()
        pass

    async def on_shutdown(self):
        logging.debug(f"on_shutdown:{__name__}")
        pass

    async def on_valves_updated(self):
        logging.debug(f"on_valves_updated:{__name__}")
        self.predownload_model()
        pass

    async def inlet(self, body: dict, user: Optional[dict] = None) -> dict:
        logging.debug(f"inlet:{__name__}")
        return body

    async def outlet(self, body: dict, user: Optional[dict] = None) -> dict:
        logging.debug(f"outlet:{__name__}")
        return body

    def predownload_model(self):
        try:
            logging.info(f"Cuda avability '{torch.cuda.is_available()}'.")
            logging.info(f"Cuda device count: '{torch.cuda.device_count()}'.")
            logging.info(f"Cuda first device name: '{torch.cuda.get_device_name(0)}'.")
        except Exception as ex:
            logging.error("Error cuda initialization: %s", ex)
        try:
            self.classifier = pipeline("zero-shot-classification", model=self.valves.ZERO_SHOT_MODEL, device=0)
            dummy_text = "This is a test."
            dummy_labels = ["test"]
            resp = self.classifier(dummy_text, dummy_labels)
            logging.info(f"Model '{self.valves.ZERO_SHOT_MODEL}' loaded and cached successfully. Response: '{resp}'.")
            return self.classifier
        except Exception as ex:
            logging.error("Error in preloading model: %s", ex)
            raise

    def classifyRequest(
            self, user_message: str, model_id: str, messages: List[dict], body: dict
    ) -> Union[str, Generator, Iterator]:
        if not user_message:
            raise Exception("Message missing in request.")

        try:
            request_data = json.loads(user_message)
        except Exception as e:
            raise Exception("Invalid JSON in user message") from e

        candidate_tags = request_data.get("candidateTags", [])
        file_name = request_data.get("fileName", "Unknown Filename")
        file_content = request_data.get("fileContent", "")
        prompt = f"File name: {file_name}\nFile content: {file_content}"
        logging.debug(f"Classification Prompt: {prompt}")

        def remove_sequence(data):
            if isinstance(data, dict):
                data.pop("sequence", None)
            else:
                for item in data:
                    item.pop("sequence", None)
            return data

        def fun_classification_multi(_):
            start = time.time()
            result = remove_sequence(self.classifier(prompt, candidate_tags, multi_label=True))
            end = time.time()
            return {"result": result, "time": end - start}

        def fun_classification_single(_):
            start = time.time()
            result = remove_sequence(self.classifier(prompt, candidate_tags, multi_label=False))
            end = time.time()
            return {"result": result, "time": end - start}

        def fun_classification_public(_):
            start = time.time()
            result = remove_sequence(self.classifier(prompt, ["public", "secret"], multi_label=False))
            end = time.time()
            return {"result": result, "time": end - start}

        parallel_classifier = RunnableParallel({
            "classification_multi": fun_classification_multi,
            "classification": fun_classification_single,
            "classification_public": fun_classification_public,
        })

        start = time.time()
        results = parallel_classifier.invoke({})
        end = time.time()
        logging.debug(f"Classification time: {end - start}. Classification response: {results}")

        return {
            "classification_multi": results["classification_multi"]["result"],
            "classification_multi_time": results["classification_multi"]["time"],
            "classification": results["classification"]["result"],
            "classification_time": results["classification"]["time"],
            "classification_public": results["classification_public"]["result"],
            "classification_public_time": results["classification_public"]["time"],
        }

    def stream_json_response(self, json_str, chunk_size=20):
        for i in range(0, len(json_str), chunk_size):
            yield json_str[i:i+chunk_size]

    def pipe(
            self, user_message: str, model_id: str, messages: List[dict], body: dict
    ) -> Union[str, Generator, Iterator]:
        start = time.time()
        logging.debug(f"pipe:{__name__}")
        os.environ["TRANSFORMERS_OFFLINE"] = "1"
        if "user" in body:
            logging.debug("######################################")
            logging.debug(f'# User: {body["user"]["name"]} ({body["user"]["id"]})')
            logging.debug(f"# Message: {user_message}")
            logging.debug("######################################")

        if user_message.__contains__("Generate a concise, 3-5 word title with an emoji summarizing the chat history."):
            logging.debug(f'# Title Generation for pipe: {__name__}')
            return json.dumps({
                "title": "Document Classification Pipeline for document: xyz"
            })

        if user_message.__contains__("Generate 1-3 broad tags categorizing the main themes of the chat history"):
            logging.debug(f'# Tag Generation for pipe: {__name__}')
            return json.dumps({ "tags": ["classification"] })


        try:
            zero_shot_resp = self.classifyRequest(
                user_message=user_message, model_id=model_id, messages=messages, body=body
            )
            data = json.dumps({
                "success": True,
                "data": zero_shot_resp,
                "error": None,
                "pipeline_time": time.time() - start,
            })
            if body.get("stream"):
                return self.stream_json_response(data)
            else:
                return data
        except Exception as e:
            error_data = json.dumps({
                "success": False,
                "data": None,
                "error": f"Error: {e}",
                "pipeline_time": time.time() - start,
            })
            logging.error(error_data)
            if body.get("stream"):
                return self.stream_json_response(error_data)
            else:
                return error_data
