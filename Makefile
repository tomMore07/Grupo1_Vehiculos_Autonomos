CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O3 -fopenmp -Wall -Wextra
BIN       = cpp/lidar_kernel
JAVA_SRC  = $(shell find java/src -name '*.java')
JAVA_TEST = $(shell find java/test -name '*.java')

all: $(BIN) java-build

$(BIN): cpp/main.cpp cpp/lidar_kernel.cpp cpp/lidar_kernel.hpp
	$(CXX) $(CXXFLAGS) cpp/main.cpp cpp/lidar_kernel.cpp -o $(BIN)

java-build:
	@mkdir -p java/build
	javac -Xlint:all -d java/build $(JAVA_SRC) $(JAVA_TEST)

# ---- Ejecución
run-cpp: $(BIN)
	./$(BIN) --n 2000000 --stages
run-py:
	cd python && python3 pipeline.py
run-integracion: $(BIN) java-build
	java -Dstdout.encoding=UTF-8 -cp java/build g1.app.Main .
benchmark: $(BIN)
	scripts/benchmark.sh

# ---- Pruebas
test-cpp: $(BIN)      # el kernel falla (código != 0) si el resultado paralelo difiere del secuencial
	@for t in 1 2 3 4 8; do ./$(BIN) --n 500000 --threads $$t > /dev/null && echo "  OK  kernel con $$t hilo(s): resultado paralelo == secuencial" || { echo "  FALLO con $$t hilos"; exit 1; }; done
test-py:
	cd python && python3 -m unittest -v
test-java: $(BIN) java-build
	java -Dstdout.encoding=UTF-8 -cp java/build g1.Pruebas
test: test-cpp test-py test-java

clean:
	rm -f $(BIN)
	rm -rf java/build python/__pycache__
.PHONY: all java-build run-cpp run-py run-integracion benchmark test-cpp test-py test-java test clean
