cmake_minimum_required(VERSION 3.22)
project(tectonic CXX)

set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build tectonic as a native executable but name it libtectonic.so
# so that Android Packaging includes it in the native libraries directory (lib/).
add_executable(libtectonic.so tectonic.cpp)

# Ensure it compiles and is linked correctly
set_target_properties(libtectonic.so PROPERTIES 
    OUTPUT_NAME "tectonic"
    PREFIX "lib"
    SUFFIX ".so"
    LINKER_LANGUAGE CXX
)
