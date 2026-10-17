package com.dk.kuiver.renderer

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.dk.kuiver.ui.EdgePath
import kotlin.math.sqrt

private const val CURVE_SAMPLES = 24

internal class EdgeHitRegistry {
    // Paths reported by EdgeCanvas and EdgeLayer
    private val paths = HashMap<String, EdgePath>()

    fun report(edgeId: String, path: EdgePath) {
        paths[edgeId] = path
    }

    fun clear(edgeId: String) {
        paths.remove(edgeId)
    }

    // Drop every edge not in edgeIds
    fun retain(edgeIds: Set<String>) {
        paths.keys.retainAll(edgeIds)
    }

    // Return the id of the edge closest to point within tolerance pixels, or null if there is none
    fun hitTest(point: Offset, tolerance: Float): String? {
        var closestId: String? = null
        var closest = tolerance
        paths.forEach { (id, path) ->
            val distance = path.distanceTo(point)
            if (distance <= closest) {
                closest = distance
                closestId = id
            }
        }
        return closestId
    }
}

/**
 * Reports the path the current edge is drawn along.
 */
internal fun interface EdgePathReporter {
    fun report(path: EdgePath?)
}

internal val LocalEdgePathReporter = staticCompositionLocalOf<EdgePathReporter?> { null }

/**
 * Returns the shortest distance from [point] to this path in pixels, arrow head included.
 */
internal fun EdgePath.distanceTo(point: Offset): Float = when (this) {
    is EdgePath.Straight -> point.distanceToSegment(from, to)
    is EdgePath.Curved -> quadraticDistance(point, from, controlPoint, pathEndpoint, to)
    is EdgePath.SelfLoop -> quadraticDistance(point, from, controlPoint, pathEndpoint, to)
    is EdgePath.Orthogonal -> {
        var min = point.distanceToSegment(pathEndpoint, to)
        var previous = from
        for (i in 1..CURVE_SAMPLES) {
            val t = i / CURVE_SAMPLES.toFloat()
            val u = 1f - t
            val next = from * (u * u * u) + controlPoint1 * (3f * u * u * t) +
                    controlPoint2 * (3f * u * t * t) + pathEndpoint * (t * t * t)
            min = minOf(min, point.distanceToSegment(previous, next))
            previous = next
        }
        min
    }

    is EdgePath.RightAngle -> {
        val points = listOf(from) + waypoints + listOf(pathEndpoint, to)
        points.zipWithNext { a, b -> point.distanceToSegment(a, b) }.min()
    }
}

private fun quadraticDistance(
    point: Offset,
    from: Offset,
    control: Offset,
    end: Offset,
    tip: Offset
): Float {
    var min = point.distanceToSegment(end, tip)
    var previous = from
    for (i in 1..CURVE_SAMPLES) {
        val t = i / CURVE_SAMPLES.toFloat()
        val u = 1f - t
        val next = from * (u * u) + control * (2f * u * t) + end * (t * t)
        min = minOf(min, point.distanceToSegment(previous, next))
        previous = next
    }
    return min
}

private fun Offset.distanceToSegment(a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) {
        0f
    } else {
        (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    }
    val px = a.x + t * dx - x
    val py = a.y + t * dy - y
    return sqrt(px * px + py * py)
}
