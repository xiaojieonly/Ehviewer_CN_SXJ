# Generate libwebp Container Spec Docs from Text Source

HTML generation requires [kramdown](https://kramdown.gettalong.org/), easily
installed as a [rubygem](https://rubygems.org/). Rubygems installation should
satisfy dependencies automatically.

HTML generation can then be done from the project root:

```shell
$ kramdown doc/webp-container-spec.txt --template doc/template.html > \
  doc/output/webp-container-spec.html
```

kramdown can optionally syntax highlight code blocks, using
[CodeRay](https://github.com/rubychan/coderay), a dependency of kramdown that
rubygems will install automatically. The following will apply inline CSS
styling; an external stylesheet is not needed.

```shell
$ kramdown doc/webp-lossless-bitstream-spec.txt \
  --template doc/template.html \
  -x syntax-coderay --syntax-highlighter coderay \
  --syntax-highlighter-opts "{default_lang: c, line_numbers: , css: style}" \
  > doc/output/webp-lossless-bitstream-spec.html
```

Optimally, use kramdown 0.13.7 or newer if syntax highlighting desired.
