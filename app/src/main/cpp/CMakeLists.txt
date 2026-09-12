cmake_minimum_required(VERSION 3.22.1)
project("llm_engine")

add_library(
        native-lib
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

# In a real setup, you would link the QNN binaries here.
# For the hackathon stub, we just compile our JNI bridge.
target_link_libraries(
        native-lib
        ${log-lib})
