# Copyright (C)2016, International Business Machines Corporation and  
# others. All Rights Reserved.                        
.PHONY: all clean

SPLC_FLAGS = -a
OUTPUT_DIR = ./output
ifeq ($(STREAMS_STUDIO_BUILDING), 1)
  # Building from Streams Studio, use env vars set by studio
  SPLC = $(STREAMS_STUDIO_SC_PATH)
  SPL_PATH = $(STREAMS_STUDIO_SPL_PATH)
  OUTPUT_DIR = $(STREAMS_STUDIO_OUTPUT_DIRECTORY)
else
  # Building outside of Streams Studio...
  ifeq ($(STREAMS_INSTALL),)
    $(error error: environment variable STREAMS_INSTALL has to be set)
  endif
  # Set the STREAMSX_RABBITMQ_TOOLKIT variable if streamsx.rabbitmq sample or toolkit location changed in your environment.
  ifeq ($(STREAMSX_RABBITMQ_TOOLKIT),)
    ifneq ($(realpath ../../../com.ibm.streamsx.rabbitmq),)
      STREAMSX_RABBITMQ_TOOLKIT = ../../../com.ibm.streamsx.rabbitmq
    endif
    ifneq ($(realpath $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.rabbitmq),)
      STREAMSX_RABBITMQ_TOOLKIT = $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.rabbitmq
    endif
  endif
  ifeq ($(STREAMSX_JSON_TOOLKIT),)
    ifneq ($(realpath $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.json),)
      STREAMSX_JSON_TOOLKIT = $(STREAMS_INSTALL)/toolkits/com.ibm.streamsx.json
    endif
  endif


  SPLC = $(STREAMS_INSTALL)/bin/sc
  SPL_PATH = $(STREAMSX_RABBITMQ_TOOLKIT):$(STREAMSX_JSON_TOOLKIT) 
endif

$(info $$STREAMSX_RABBITMQ_TOOLKIT is [${STREAMSX_RABBITMQ_TOOLKIT}])
$(info $$SPL_PATH is [${SPL_PATH}])


SPLC_FLAGS += -t $(SPL_PATH)

SPLC = $(STREAMS_INSTALL)/bin/sc

SPL_CMD_ARGS ?=
SPL_MAIN_COMPOSITE = com.ibm.streamsx.rabbitmq.sample::RabbitMQRestServer


all:
	$(SPLC) $(SPLC_FLAGS) -M $(SPL_MAIN_COMPOSITE) --output-dir $(OUTPUT_DIR) $(SPL_CMD_ARGS)

clean:
	$(SPLC) $(SPLC_FLAGS) -C -M $(SPL_MAIN_COMPOSITE)












