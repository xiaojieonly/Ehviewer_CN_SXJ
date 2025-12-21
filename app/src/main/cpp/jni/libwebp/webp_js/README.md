# WebP JavaScript decoder

```
 __   __ ____ ____ ____     __  ____
/  \\/  \  _ \  _ \  _ \   (__)/  __\
\       /  __/ _  \  __/   _)  \_   \
 \__\__/_____/____/_/     /____/____/
```

This file describes the compilation of libwebp into a JavaScript decoder using
Emscripten and CMake.

-   install the Emscripten SDK following the procedure described at:
    https://emscripten.org/docs/getting_started/downloads.html#installation-instructions-using-the-emsdk-recommended
    After installation, you should have some global variable positioned to the
    location of the SDK. In particular, $EMSDK should point to the top-level
    directory containing Emscripten tools.

-   configure the project 'WEBP_JS' with CMake using:

    ```shell
    cd webp_js && \
    emcmake cmake -DWEBP_BUILD_WEBP_JS=ON ../
    ```

-   compile webp.js using 'emmake make'.

-   that's it! Upon completion, you should have the 'webp.js', 'webp.js.mem',
    'webp_wasm.js' and 'webp_wasm.wasm' files generated.

The callable JavaScript function is WebPToSDL(), which decodes a raw WebP
bitstream into a canvas. See webp_js/index.html for a simple usage sample (see
below for instructions).

## Demo HTML page

The HTML page webp_js/index.html requires the built files 'webp.js' and
'webp.js.mem' to be copied to webp_js/. An HTTP server to serve the WebP image
example is also needed. With Python, just run:

```shell
cd webp_js && python3 -m http.server 8080
```

and then navigate to http://localhost:8080 in your favorite browser.

## Web-Assembly (WASM) version:

CMakeLists.txt is configured to build the WASM version when using the option
WEBP_BUILD_WEBP_JS=ON. The compilation step will assemble the files
'webp_wasm.js' and 'webp_wasm.wasm' that you then need to copy to the webp_js/
directory.

See webp_js/index_wasm.html for a simple demo page using the WASM version of the
library.

## Caveats

-   First decoding using the library is usually slower, due to just-in-time
    compilation.
