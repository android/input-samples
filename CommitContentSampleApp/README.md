
Android CommitContentSampleApp Sample
===================================

This sample demonstrates how to write an application which accepts rich content (such as images)
sent from a keyboard using the Commit Content API.

Introduction
------------

Users often want to communicate with emojis, stickers, and other kinds of rich content. In previous
versions of Android, soft keyboards (input method editors or IMEs) could send only unicode emoji to
apps. For rich content (such as images), apps had to either build app-specific APIs that couldn't
be used in other apps or use workarounds like sending images through the Easy Share Action or the
clipboard.

Now in Android 7.1 (API 25), the Android SDK includes the [Commit Content API][1], which provides a
universal way for IMEs to send images and other rich content directly to a text editor in an app.
The API is also available in the v13 Support Library (ver. 25.0), supporting devices as early as
Android 3.2 (API 13).

With this API, you can build messaging apps that accept rich content from any keyboard, as well as
keyboards that can send rich content to any app.

[1]: https://android-dot-devsite.googleplex.com/preview/image-keyboard.html

Pre-requisites
--------------

- Android SDK 28
- Android Build Tools v28.0.3
- Android Support Repository

Screenshots
-------------

<img src="screenshots/screenshot-1.png" height="400" alt="Screenshot"/> 

Getting Started
---------------

This sample uses the Gradle build system. To build this project, use the
"gradlew build" command or use "Import Project" in Android Studio.

Support
-------

- Stack Overflow: http://stackoverflow.com/questions/tagged/android

If you've found an error in this sample, please file an issue:
https://github.com/android/input

Patches are encouraged, and may be submitted by forking this project and
submitting a pull request through GitHub. Please see CONTRIBUTING.md for more details.
