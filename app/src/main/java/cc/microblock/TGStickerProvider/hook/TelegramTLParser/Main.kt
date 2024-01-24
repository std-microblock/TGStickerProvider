import java.lang.reflect.Field

fun toStringRecursive(obj: Any, indent: Int = 0, skipFirstIndent: Boolean = false): String {
    val className = obj.javaClass.simpleName
    val fields = getAllFields(obj.javaClass)

    val result = StringBuilder()
    if (skipFirstIndent) result.append("$className {\n")
    else result.append("${"  ".repeat(indent)}$className {\n")

    for (field in fields) {
        try {
            field.isAccessible = true
        } catch (e: Exception) {
            continue
        }
        val fieldName = field.name
        val value = field.get(obj)

        result.append("${"  ".repeat(indent + 1)}$fieldName: ")

        when {
            value is Collection<*> -> {
                // Handle collections
                result.append("[\n")
                val collectionIndent = indent + 2
                for ((index, element) in (value as Collection<*>).withIndex()) {
                    result.append(
                        "${"  ".repeat(collectionIndent)}[$index] = ${
                            toStringRecursive(
                                element!!,
                                collectionIndent + 1,
                                true
                            )
                        }\n"
                    )
                }
                result.append("${"  ".repeat(indent + 1)}]\n")
            }

            value is Array<*> -> {
                // Handle arrays
                result.append("[\n")
                val arrayIndent = indent + 2
                for ((index, element) in (value as Array<*>).withIndex()) {
                    result.append(
                        "${"  ".repeat(arrayIndent)}[$index] = ${
                            toStringRecursive(
                                element!!,
                                arrayIndent + 1,
                                true
                            )
                        }\n"
                    )
                }
                result.append("${"  ".repeat(indent + 1)}]\n")
            }

            value != null && !field.type.isPrimitive && !field.type.isAssignableFrom(String::class.java) -> {
                // Handle non-primitive and non-string fields
                result.append("${toStringRecursive(value, indent + 1, true)}\n")
            }

            else -> {
                // Handle primitive and string fields
                result.append("$value\n")
            }
        }
    }

    result.append("${"  ".repeat(indent)}}")
    return result.toString()
}


fun getAllFields(cls: Class<*>): List<Field> {
    val fields = cls.declaredFields.toMutableList()
    val superClass = cls.superclass

    if (superClass != null) {
        fields.addAll(getAllFields(superClass))
    }

    return fields
}

//
// fun main(args: Array<String>) {
//    val stream = SerializedData(
////        File("J:/file_to_path.db")
//        File("C:\\Users\\MicroBlock\\Downloads\\cache4.db-stickersets-34-data.bin")
//    )
//
//    val set = TLRPC.TL_messages_stickerSet.TLdeserialize(stream, stream.readInt32(true), true)
//
//    println(toStringRecursive(set))
//}
