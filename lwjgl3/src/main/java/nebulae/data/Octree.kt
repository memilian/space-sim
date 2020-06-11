package nebulae.data

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import ktx.math.times
import nebulae.kutils.minus
import nebulae.kutils.plus

/*
//Octree: Octants numbering
//
//             +Z                          +Y
//             |                           /
//             |                          /
//             |                         /
//             |
//             |       o---------------o---------------o
//             |      /               /               /|
//             |     /       3       /       7       / |
//             |    /               /               /  |
//             |   o---------------o---------------o   |
//             |  /               /               /|   |
//             | /       2       /       6       / | 7 |
//             /                /               /  |   o
//             o---------------o---------------o   |  /|
//             |               |               |   | / |
//             |               |               | 6 |/  |
//             |               |               |   o   |
//             |       2       |       6       |  /|   |
//             |               |               | / | 5 |
//             |               |               |/  |   o
//             o---------------o---------------o   |  /
//             |               |               |   | /
//             |               |               | 4 |/
//             |               |               |   o
//             |       0       |       4       |  /
//             |               |               | /
//             |               |               |/
//             o---------------o---------------o -----------------+X
//
//
//
*/

class Octree(val bounds: BoundingBox, val depth: Int = 0, val parent: Octree? = null) : Iterable<Octree>, Disposable {
    var children: Array<Octree>? = null

    val MAX_DEPTH = 8
    val MAX_OBJECT = 100

    val isLeaf: Boolean
        get() : Boolean {
            return children == null
        }

    val objects = mutableMapOf<String, MutableList<*>>()

    fun <T : BaseObject> insert(obj: T) {
        if (!bounds.contains(obj.boundingBox)) {
            throw IllegalStateException("Cannot insert out of bounds object $obj")
        }
        if (!isLeaf) {
            val index = getIndex(obj)
            if (index != -1) {
                children!![index].insert(obj)
                return
            }
        }
        if (!isLeaf) {
            println("noleaf")
        }
        val type: String = obj::class.java.simpleName

        @Suppress("UNCHECKED_CAST")
        var typedList: MutableList<BaseObject>? = objects[type] as MutableList<BaseObject>?
        if (typedList == null) {
            typedList = mutableListOf()
            objects[type] = typedList
        }
        typedList.add(obj)

        if (isLeaf && typedList.size > MAX_OBJECT && depth < MAX_DEPTH) {
            subdivide()
            val elements = mutableListOf<BaseObject>()
            elements.addAll(typedList)
            typedList.clear()
            elements.forEach {
                insert(it)
            }
        }
    }

    private val tmp = Vector3()
    fun intersect(ray: Ray, result: MutableList<Octree>) {
        tmp.scl(0f)
        if (Intersector.intersectRayBounds(ray, bounds, tmp)) {
            if (isLeaf) {
                if (objects.isNotEmpty()) {
                    result.add(this)
                }
            } else {
                for (child in children!!) {
                    child.intersect(ray, result)
                }
            }
        }
    }

    private val sphereBB = BoundingBox()
    fun intersectSphere(center: Vector3, radius: Float): MutableList<Octree> {
        val result = mutableListOf<Octree>()
        sphereBB.min.set(tmp.set(center) - radius)
        sphereBB.max.set(tmp.set(center) - radius)
        result.addAll(select {
            it.isLeaf && (sphereBB.contains(it.bounds) || bounds.contains(sphereBB))
        })
        return result
    }

    private fun getSelfObjectCount(): Int {
        return objects.values.sumBy {
            it.size
        }
    }

    private fun <T : BaseObject> getIndex(obj: T): Int {
        for ((index, child) in children!!.withIndex()) {
            if (child.bounds.contains(obj.boundingBox)) {
                return index
            }
        }
        return -1
    }

    private fun contains(boundingBox: BoundingBox): Boolean {
        return this.bounds.contains(boundingBox)
    }

    private fun subdivide() {
        if (!isLeaf) {
            throw IllegalStateException("Node is already divided $this")
        }
        val subSize = Vector3(
                bounds.width / 2,
                bounds.height / 2,
                bounds.depth / 2
        )

        val center = Vector3()
        bounds.getCenter(center)

        children = arrayOf(
                Octree(BoundingBox(center.cpy().add(Vector3(-1f, -1f, -1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(-1f, 1f, -1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(-1f, -1f, 1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(-1f, 1f, 1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(1f, -1f, -1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(1f, 1f, -1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(1f, -1f, 1f) * subSize), center.cpy()), depth + 1, this),
                Octree(BoundingBox(center.cpy().add(Vector3(1f, 1f, 1f) * subSize), center.cpy()), depth + 1, this)
        )
    }

    override fun toString(): String {
        return """
            $bounds
        """.trimIndent()
    }

    override fun dispose() {
        objects.clear()
        if (!isLeaf) {
            for (child in children!!) {
                child.dispose()
            }
            children = null
        }
    }

    fun printStats() {

        println("Octree with ${countNodes()} nodes")

        val objectCount = getObjectCounts(false, mutableMapOf());
        for (count in objectCount) {
            println(count.key + "  =>  " + count.value);
        }

    }

    private fun countNodes(): Int {
        var count = 1
        if (!isLeaf) {
            for (child in children!!) {
                count += child.countNodes()
            }
        }
        return count
    }

    private fun getObjectCounts(justSelf: Boolean, objs: MutableMap<String, Int>): MutableMap<String, Int> {
        for (obj in objects) {
            if (objs[obj.key] == null) {
                objs[obj.key] = 0
            }
            val count: Int = objs[obj.key]!!
            objs[obj.key] = count + obj.value.size
        }
        if (!justSelf && !isLeaf) {
            for (child in children!!) {
                child.getObjectCounts(false, objs)
            }
        }
        return objs
    }

    override fun iterator(): Iterator<Octree> {
        return OctreeIterator(this)
    }

    fun visit(visitor: (Octree) -> Unit) {
        visitor(this)
        if (!isLeaf) {
            for (child in children!!) {
                child.visit(visitor)
            }
        }
    }

    fun select(selector: (Octree) -> Boolean): List<Octree> {
        val res = mutableListOf<Octree>()
        visit {
            if (selector(it)) {
                res.add(it)
            }
        }
        return res;
    }

    fun leafs(): List<Octree> {
        return select { it.isLeaf }
    }

    val emptyResult = mutableListOf<Any>()
    inline fun <reified T> getObjects(): List<T> {
        return objects.getOrDefault(T::class.java.simpleName, emptyResult as MutableList<T>) as List<T>
    }

}

class OctreeIterator(octree: Octree) : Iterator<Octree> {
    private val nodes = mutableListOf<Octree>()
    private var index = 0

    init {
        octree.visit {
            nodes.add(it)
        }
    }

    override fun hasNext(): Boolean {
        return index < nodes.size
    }

    override fun next(): Octree {
        return nodes[index++]
    }

}
