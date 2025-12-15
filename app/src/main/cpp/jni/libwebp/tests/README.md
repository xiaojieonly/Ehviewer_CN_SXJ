# Tests

This is a collection of tests for the libwebp libraries, currently covering
fuzzing through the APIs. Additional test vector coverage can be found at:
https://chromium.googlesource.com/webm/libwebp-test-data

## Building

### Fuzzers

Follow the [build instructions](../doc/building.md) for libwebp, optionally
adding build flags for various sanitizers (e.g., -fsanitize=address).

`-DWEBP_BUILD_FUZZTEST=ON` can then be used to compile the fuzzer targets:

```shell
$ cmake -B ./build -S . -DWEBP_BUILD_FUZZTEST=ON
$ make -C build
```
