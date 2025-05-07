# **AI Stack**
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  
An example integration of multiple AI-related components with Alfresco as the content repository to enable Retrieval-Augmented Generation (RAG) for chat-based responses.

## **Prerequisites**
- Java (17.0.9)
- Maven (3.9.5)
- Angular (18.1.0)
- Node.js (v20.9.0)
- NPM (10.1.0)
- Docker \[tested on Windows using Docker Desktop (v4.40.0) with WSL2\]

### Local environment notes
Recommended limits for wsl in **C:/Users/<current_user>/.wslconfig**
```sh
[wsl2]
memory=34GB 
processors=6
```
NVIDIA GPU notes - additional drivers for docker:  
1. NVIDIA container-toolkit:  
   - https://docs.docker.com/desktop/features/gpu/  
   - https://developer.nvidia.com/cuda/wsl  
   - https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html  
2. Test:  
```sh
docker run --rm -it --gpus=all nvcr.io/nvidia/k8s/cuda-sample:nbody nbody -gpu -benchmark
```
**Note:** If the command hangs without producing any output, the issue may be related to the Docker Desktop version. Ensure that Docker Desktop is version 4.18 or higher, or exactly 4.17.0.
This behavior has been observed and resolved in version 4.40.0.  
Source: [Stack Overflow](https://stackoverflow.com/questions/75809278/running-docker-desktop-containers-with-gpus-tag-hangs-without-any-response-in)

# **Architecture**
![generalArch](./docs/drawio/ai-stack-arch-Dark.drawio.svg)
![generalArch](./docs/drawio/ai-stack-Pipelines-Dark.drawio.svg)
![generalArch](./docs/drawio/ai-stack-Ollama-Dark.drawio.svg)
![generalArch](./docs/drawio/ai-stack-VectorDB-Dark.drawio.svg)

# Manual steps after deployment
## Pipelines configuration
1. Navigate to http://\<your_domain\>/basic-panel/chat (OpenWebUI chat window) and [obtain your API key (AI_PIPELINE_API_KEY env)](https://docs.openwebui.com/getting-started/api-endpoints/).
2. [Connect Open WebUI with Pipelines](https://docs.openwebui.com/tutorials/integrations/langfuse#step-3-connecting-open-webui-with-pipelines)
4. Set the API URL to http://ai-pipelines:9099 and the API key to 0p3n-w3bu!.

*For full documentation see: https://docs.openwebui.com/pipelines.*

## Development
1. Clone current repository.
2. Fix git autocrlf:
`git config core.autocrlf input`.
3. Update environment variables in `./docker/.env`.
3. Build components and deploy them locally:`./run.sh build_start`.

# Technologies and tools used
- [Alfresco](https://docs.alfresco.com/)
- [Alfresco Application Development Framework](https://www.alfresco.com/abn/adf/docs/)
- [Open WebUI](https://docs.openwebui.com/)  
- [Pipelines by Open WebUI](https://docs.openwebui.com/pipelines/)
- [aborroy/alfresco-ai-framework](https://github.com/aborroy/alfresco-ai-framework/)
- [Ollama](https://ollama.com/)
- [Elasticsearch](https://github.com/elastic/elasticsearch/)

Sources of AI models are being downloaded from:  
- [Ollama](https://ollama.com/search)
- [Huggingface](https://huggingface.co/models)

Models:  
- [Qwen3-8B-q4_K_M](https://qwenlm.github.io/blog/qwen3)
- [mxbai-embed-large:335m-v1-fp16](https://www.mixedbread.com/blog/mxbai-embed-large-v1)
- [MoritzLaurer/mDeBERTa-v3-base-mnli-xnli](https://huggingface.co/MoritzLaurer/mDeBERTa-v3-base-mnli-xnli)

# License
This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.

# Acknowledgments
Special thanks to the [Alfresco](https://www.hyland.com/en/solutions/products/alfresco-platform) and [Hyland](https://www.hyland.com/en) teams for their ongoing commitment to open-source innovation in the fields of content management and artificial intelligence.  
We also extend our sincere gratitude to [Angel Borroy](https://github.com/aborroy) for his valuable work on the [alfresco-ai-framework](https://github.com/aborroy/alfresco-ai-framework), which served as an inspiration and foundation for parts of this project.
