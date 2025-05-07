#!/bin/sh

export COMPOSE_FILE_PATH="${PWD}/target/classes/docker/docker-compose.yml"

if [ -z "${M2_HOME}" ]; then
  export MVN_EXEC="mvn"
else
  export MVN_EXEC="${M2_HOME}/bin/mvn"
fi

start() {
    docker network create -d bridge ai_cloud
    docker volume create ai-stack-openwebui-volume
    docker volume create ai-stack-elastic-volume
    docker volume create ai-stack-ollama-models-volume
    docker volume create ai-stack-ollama-cache-volume
    docker volume create ai-stack-acs-volume
    docker volume create ai-stack-db-volume
    docker volume create ai-stack-ass-volume
    docker volume create ai-stack-pipelines-cache-volume
    docker compose -f "$COMPOSE_FILE_PATH" up --build -d
}

start_share() {
    docker compose -f "$COMPOSE_FILE_PATH" up --build -d ai-stack-share
}

start_acs() {
    docker compose -f "$COMPOSE_FILE_PATH" up --build -d alfresco
}

down() {
    if [ -f "$COMPOSE_FILE_PATH" ]; then
        docker compose -f "$COMPOSE_FILE_PATH" down
    fi
}

purge() {
    docker volume rm -f ai-stack-openwebui-volume
    docker volume rm -f ai-stack-elastic-volume
    docker volume rm -f ai-stack-ollama-models-volume
    docker volume rm -f ai-stack-ollama-cache-volume
    docker volume rm -f ai-stack-acs-volume
    docker volume rm -f ai-stack-db-volume
    docker volume rm -f ai-stack-ass-volume
    docker volume rm -f ai-stack-pipelines-cache-volume
    docker network rm ai_cloud
}

build() {
    $MVN_EXEC clean install -DskipTests -DupdateCache
}

build_share() {
    docker compose -f "$COMPOSE_FILE_PATH" kill ai-stack-share
    yes | docker compose -f "$COMPOSE_FILE_PATH" rm -f ai-stack-share
    $MVN_EXEC clean install -pl ai-stack-share,ai-stack-share-docker
}

build_acs() {
    docker compose -f "$COMPOSE_FILE_PATH" kill alfresco
    yes | docker compose -f "$COMPOSE_FILE_PATH" rm -f alfresco
    $MVN_EXEC clean install -pl ai-stack-integration-tests,ai-stack-platform,ai-stack-platform-docker
}

tail() {
    docker compose -f "$COMPOSE_FILE_PATH" logs -f
}

tail_all() {
    docker compose -f "$COMPOSE_FILE_PATH" logs --tail="all"
}

prepare_test() {
    $MVN_EXEC verify -DskipTests=true -pl ai-stack-platform,ai-stack-integration-tests,ai-stack-platform-docker
}

test() {
    $MVN_EXEC verify -pl ai-stack-platform,ai-stack-integration-tests
}

case "$1" in
  build_start)
    down
    build
    start
    tail
    ;;
  build_start_it_supported)
    down
    build
    prepare_test
    start
    tail
    ;;
  start)
    start
    tail
    ;;
  stop)
    down
    ;;
  purge)
    down
    purge
    ;;
  tail)
    tail
    ;;
  reload_share)
    build_share
    start_share
    tail
    ;;
  reload_acs)
    build_acs
    start_acs
    tail
    ;;
  build_test)
    down
    build
    prepare_test
    start
    test
    tail_all
    down
    ;;
  test)
    test
    ;;
  *)
    echo "Usage: $0 {build_start|build_start_it_supported|start|stop|purge|tail|reload_share|reload_acs|build_test|test}"
esac