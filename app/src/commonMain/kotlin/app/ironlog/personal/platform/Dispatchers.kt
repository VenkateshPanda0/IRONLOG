package app.ironlog.personal.platform

import kotlinx.coroutines.CoroutineDispatcher

/** The dispatcher for blocking file and network work. */
expect val ioDispatcher: CoroutineDispatcher
