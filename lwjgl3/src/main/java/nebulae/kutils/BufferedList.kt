package nebulae.kutils

class BufferedList<T>(private val base: MutableList<T> = mutableListOf()) : MutableList<T> by base {
    private val buffer = mutableListOf<T>()

    public override fun add(element: T): Boolean {
        return buffer.add(element)
    }

    public override fun addAll(elements: Collection<T>): Boolean {
        return buffer.addAll(elements)
    }

    public fun update() {
        base.addAll(buffer)
        buffer.clear()
    }
}