package nebulae.geom

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils.clamp
import com.sudoplay.joise.noise.Noise
import wblut.hemesh.*
import java.lang.UnsupportedOperationException
import kotlin.math.sqrt


fun createQuadSphere(resolution: Int): HE_Mesh {
    val creator = HEC_Box()
    creator.setSize(0.5, 0.5, 0.5)
            .setSegments(resolution, resolution, resolution)
    val heMesh = HE_Mesh(creator)
    heMesh.modify(BoxToSphereModifier())
    return heMesh
}

fun getModelFromHeMesh(heMesh: HE_Mesh): Model {
    heMesh.triangulate()
    val vertCount = heMesh.triangles.size * 6
    val vertices = FloatArray(vertCount)
    println("Quadsphere : creating mesh with $vertCount vertices")

    var idx = 0
    val fItr: HE_FaceIterator = heMesh.fItr()
    while (fItr.hasNext()) {
        val f = fItr.next() as HE_Face
        var he = f.halfedge
        val n = f.faceDegree
        for (i in 0 until n) {
            vertices[idx++] = he.vertex.position.xf()
            vertices[idx++] = he.vertex.position.yf()
            vertices[idx++] = he.vertex.position.zf()
            vertices[idx++] = he.vertex.vertexNormal.xf()
            vertices[idx++] = he.vertex.vertexNormal.yf()
            vertices[idx++] = he.vertex.vertexNormal.zf()
            he = he.nextInFace
        }
    }

    val mesh = Mesh(Mesh.VertexDataType.VertexArray, true, vertCount * 6, 0, VertexAttribute.Position(), VertexAttribute.Normal())
    mesh.setVertices(vertices)
    val mb = ModelBuilder()
    mb.begin()
    mb.node()
    mb.part(MeshPart("quadsphere", mesh, 0, mesh.numVertices, GL20.GL_TRIANGLES), Material())
    return mb.end()
}

class BoxToSphereModifier : HEM_Modifier() {
    override fun applySelf(mesh: HE_Mesh): HE_Mesh {
        mesh.vertices.forEach {
            val x = it.xd()
            val y = it.yd()
            val z = it.zd()

            val xs = x * x
            val ys = y * y
            val zs = z * z

            it.setX(x * sqrt(1 - ys * 0.5 - zs * 0.5 + ys * zs / 3))
            it.setY(y * sqrt(1 - xs * 0.5 - zs * 0.5 + xs * zs / 3))
            it.setZ(z * sqrt(1 - ys * 0.5 - xs * 0.5 + ys * xs / 3))
            it.position.normalizeSelf()
        }
        return mesh
    }

    override fun applySelf(mesh: HE_Selection): HE_Mesh {
        throw UnsupportedOperationException()
    }

}

class NoiseModifier : HEM_Modifier() {
    var seed: Int = 123
    var scale = 1.0
    override fun applySelf(mesh: HE_Mesh): HE_Mesh {
        mesh.vertices.forEach {
            val x = (it.xd() * scale * 1000).toInt()
            val y = (it.yd() * scale * 1000).toInt()
            val z = (it.zd() * scale * 1000).toInt()

            val height = clamp(Noise.valueNoise3D(x, y, z, seed), 0.0, 1.0)
            it.position.mul(0.5 + height);
        }
        return mesh
    }

    override fun applySelf(mesh: HE_Selection): HE_Mesh {
        throw UnsupportedOperationException()
    }

}