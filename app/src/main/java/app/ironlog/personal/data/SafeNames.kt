package app.ironlog.personal.data

/**
 * Photo file names come from the database, which a restored backup can fill. Only plain names
 * are accepted, so no stored value can point outside its folder (no "../", no separators).
 */
fun safeFileName(name: String): String? = name.takeIf { it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,119}")) && ".." !in it }
