package app.ironlog.personal.platform

import okio.FileSystem

/** The phone's own file system (the app's private storage lives on it). */
expect val appFileSystem: FileSystem
