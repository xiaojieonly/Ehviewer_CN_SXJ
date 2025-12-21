#!/usr/bin/python

"""distutils script for libwebp python module."""

from distutils.core import setup
from distutils.extension import Extension
import os
import shutil
import tempfile

tmpdir = tempfile.mkdtemp()
package = "com.google.webp"
package_path = os.path.join(tmpdir, *package.split("."))
os.makedirs(package_path)

# Create __init_.py files along the package path.
initpy_path = tmpdir
for d in package.split("."):
  initpy_path = os.path.join(initpy_path, d)
  open(os.path.join(initpy_path, "__init__.py"), "w").close()

shutil.copy2("libwebp.py", package_path)
setup(name="libwebp",
      version="0.0",
      description="libwebp python wrapper",
      long_description="Provides access to 'simple' libwebp decode interface",
      license="BSD",
      url="http://developers.google.com/speed/webp",
      ext_package=package,
      ext_modules=[Extension("_libwebp",
                             ["libwebp_python_wrap.c"],
                             libraries=["webp"],
                            ),
                  ],
      package_dir={"": tmpdir},
      packages=["com", "com.google", "com.google.webp"],
      py_modules=[package + ".libwebp"],
     )

shutil.rmtree(tmpdir)
