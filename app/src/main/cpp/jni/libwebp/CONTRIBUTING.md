# How to Contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution;
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code reviews

All submissions, including submissions by project members, require review. We
use a [Gerrit](https://www.gerritcodereview.com) instance hosted at
https://chromium-review.googlesource.com for this purpose.

## Sending patches

The basic git workflow for modifying libwebp code and sending for review is:

1.  Get the latest version of the repository locally:

    ```sh
    git clone https://chromium.googlesource.com/webm/libwebp && cd libwebp
    ```

2.  Copy the commit-msg script into ./git/hooks (this will add an ID to all of
    your commits):

    ```sh
    curl -Lo .git/hooks/commit-msg https://chromium-review.googlesource.com/tools/hooks/commit-msg && chmod u+x .git/hooks/commit-msg
    ```

3.  Modify the local copy of libwebp. Make sure the code
    [builds successfully](https://chromium.googlesource.com/webm/libwebp/+/HEAD/doc/building.md#cmake).

4.  Choose a short and representative commit message:

    ```sh
    git commit -a -m "Set commit message here"
    ```

5.  Send the patch for review:

    ```sh
    git push https://chromium-review.googlesource.com/webm/libwebp HEAD:refs/for/main
    ```

    Go to https://chromium-review.googlesource.com to view your patch and
    request a review from the maintainers.

See the
[WebM Project page](https://www.webmproject.org/code/contribute/submitting-patches/)
for additional details.

## Code Style

The C code style is based on the
[Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) and
`clang-format --style=Google`, though this project doesn't use the tool to
enforce the formatting.

CMake files are formatted with
[cmake-format](https://cmake-format.readthedocs.io/en/latest/). `cmake-format
-i` can be used to format individual files, it will use the settings from
`.cmake-format.py`.

## Community Guidelines

This project follows
[Google's Open Source Community Guidelines](https://opensource.google.com/conduct/).
