SIPI_VERSION := 2.0.1
GRAPHDB_SE_VERSION := 9.0.0
GRAPHDB_FREE_VERSION := 9.0.0
_GRAPHDB_HEAP_SIZE := 5G

REPO_PREFIX := daschswiss
KNORA_API_REPO := knora-api
KNORA_GRAPHDB_SE_REPO := knora-graphdb-se
KNORA_GRAPHDB_FREE_REPO := knora-graphdb-free
KNORA_SIPI_REPO := knora-sipi
KNORA_ASSETS_REPO := knora-assets
KNORA_UPGRADE_REPO := knora-upgrade
KNORA_SALSAH1_REPO := knora-salsah1
KNORA_WEBAPI_DB_CONNECTIONS := 2
KNORA_GRAPHDB_REPOSITORY_NAME := knora-test

ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git describe --tag --dirty --abbrev=7)
endif
ifeq ($(BUILD_TAG),)
  BUILD_TAG := $(shell git rev-parse --verify HEAD)
endif

ifeq ($(KNORA_API_IMAGE),)
  KNORA_API_IMAGE := $(REPO_PREFIX)/$(KNORA_API_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_GRAPHDB_SE_IMAGE),)
  KNORA_GRAPHDB_SE_IMAGE := $(REPO_PREFIX)/$(KNORA_GRAPHDB_SE_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_GRAPHDB_FREE_IMAGE),)
  KNORA_GRAPHDB_FREE_IMAGE := $(REPO_PREFIX)/$(KNORA_GRAPHDB_FREE_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_SIPI_IMAGE),)
  KNORA_SIPI_IMAGE := $(REPO_PREFIX)/$(KNORA_SIPI_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_ASSETS_IMAGE),)
  KNORA_ASSETS_IMAGE := $(REPO_PREFIX)/$(KNORA_ASSETS_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_UPGRADE_IMAGE),)
  KNORA_UPGRADE_IMAGE := $(REPO_PREFIX)/$(KNORA_UPGRADE_REPO):$(BUILD_TAG)
endif

ifeq ($(KNORA_SALSAH1_IMAGE),)
  KNORA_SALSAH1_IMAGE := $(REPO_PREFIX)/$(KNORA_SALSAH1_REPO):$(BUILD_TAG)
endif

ifeq ($(GIT_EMAIL),)
  GIT_EMAIL := $(shell git config user.email)
endif

ifeq ($(KNORA_GDB_LICENSE),)
  KNORA_GDB_LICENSE := unknown
endif

ifeq ($(KNORA_GDB_IMPORT),)
  KNORA_GDB_IMPORT := unknown
endif

ifeq ($(KNORA_GDB_HOME),)
  KNORA_GDB_HOME := unknown
endif

ifeq ($(GRAPHDB_HEAP_SIZE),)
  KNORA_GDB_HEAP_SIZE := $(_GRAPHDB_HEAP_SIZE)
else
  KNORA_GDB_HEAP_SIZE := $(GRAPHDB_HEAP_SIZE)
endif

UNAME := $(shell uname)
ifeq ($(UNAME),Darwin)
  DOCKERHOST :=  $(shell ifconfig en0 | grep inet | grep -v inet6 | cut -d ' ' -f2)
else
  DOCKERHOST := $(shell ip -4 addr show docker0 | grep -Po 'inet \K[\d.]+')
endif