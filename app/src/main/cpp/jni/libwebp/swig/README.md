# SWIG bindings

## Building

### JNI SWIG bindings

```shell
 $ gcc -shared -fPIC -fno-strict-aliasing -O2 \
       -I/path/to/your/jdk/includes \
       libwebp_java_wrap.c \
       -lwebp \
       -o libwebp_jni.so
```

Example usage:

```java
import com.google.webp.libwebp;

import java.lang.reflect.Method;

public class libwebp_jni_example {
  static {
    System.loadLibrary("webp_jni");
  }

  /**
   * usage: java -cp libwebp.jar:. libwebp_jni_example
   */
  public static void main(String argv[]) {
    final int version = libwebp.WebPGetDecoderVersion();
    System.out.println("libwebp version: " + Integer.toHexString(version));

    System.out.println("libwebp methods:");
    final Method[] libwebpMethods = libwebp.class.getDeclaredMethods();
    for (int i = 0; i < libwebpMethods.length; i++) {
      System.out.println(libwebpMethods[i]);
    }
  }
}
```

```shell
 $ javac -cp libwebp.jar libwebp_jni_example.java
 $ java -Djava.library.path=. -cp libwebp.jar:. libwebp_jni_example
```

### Python SWIG bindings:

```shell
 $ python setup.py build_ext
 $ python setup.py install --prefix=pylocal
```

Example usage:

```python
import glob
import sys
sys.path.append(glob.glob('pylocal/lib/python*/site-packages')[0])

from com.google.webp import libwebp
print "libwebp decoder version: %x" % libwebp.WebPGetDecoderVersion()

print "libwebp attributes:"
for attr in dir(libwebp): print attr
```
