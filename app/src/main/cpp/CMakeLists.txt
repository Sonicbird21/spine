cmake_minimum_required(VERSION 3.22.1)
project(spinenative)

find_package(shadowhook REQUIRED CONFIG)

add_library(spinenative SHARED session_protect.cpp)

find_library(log-lib log)
target_link_libraries(spinenative shadowhook::shadowhook ${log-lib})
