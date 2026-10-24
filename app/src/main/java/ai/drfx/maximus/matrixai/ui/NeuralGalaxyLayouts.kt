package ai.drfx.maximus.matrixai.ui

import kotlin.math.*

/** Presentation preferences never own graph facts. Brain is the preserved original default. */
internal enum class NeuralGalaxyLayout(val title: String) {
    BRAIN("Brain"), RINGS("Rings"), CIRCLE("Circle"), AREAS("Areas"),
    LINKS("Links"), TIMELINE("Timeline"), ORBIT("3D Orbit")
}

/** Immutable read-only projection. A module definition is not an observed running service. */
internal data class GalaxyNode(
    val id: String, val area: String, val layer: Int, val initial: SpacePoint,
    val changedAt: Long? = null, val runtimeObserved: Boolean = false
)
internal data class GalaxyEdge(val source: String, val target: String)
/** Each mark references one committed Room event; summaries are untrusted display text. */
internal enum class GalaxyTimestampIssue { NONE, MISSING, INVALID, FUTURE }
internal data class GalaxyActivity(
    val id: String, val timestamp: Long?, val source: String, val target: String?,
    val type: String, val summary: String,
    val timestampIssue: GalaxyTimestampIssue = when {
        timestamp == null || timestamp == 0L -> GalaxyTimestampIssue.MISSING
        timestamp < 0L -> GalaxyTimestampIssue.INVALID
        else -> GalaxyTimestampIssue.NONE
    }
) {
    val chronological: Boolean get() = timestampIssue == GalaxyTimestampIssue.NONE && timestamp != null && timestamp > 0L
}
internal data class GalaxyGeometry(
    val positions: Map<String, SpacePoint>,
    val areaLabels: Map<String, SpacePoint> = emptyMap(),
    val firstTimestamp: Long? = null,
    val lastTimestamp: Long? = null,
    val undatedCount: Int = 0,
    val activityPositions: Map<String, SpacePoint> = emptyMap()
)

/**
 * Deterministic geometry, prepared away from the UI thread and cached until source/layout changes.
 * Every mode retains exactly the same node IDs. Missing timestamps stay explicitly undated.
 * Force preparation uses a fixed iteration/repulsion budget; it never runs in a draw loop.
 */
internal object NeuralGalaxyLayouts {
    fun clockChecked(record: GalaxyActivity, now: Long): GalaxyActivity {
        require(now >= 0L)
        return record.copy(timestampIssue = when {
            record.timestamp == null || record.timestamp == 0L -> GalaxyTimestampIssue.MISSING
            record.timestamp < 0L -> GalaxyTimestampIssue.INVALID
            record.timestamp > now -> GalaxyTimestampIssue.FUTURE
            else -> GalaxyTimestampIssue.NONE
        })
    }
    /** Brain retains its exact original projection. Alternate layouts reserve HUD/footer space. */
    fun viewport(mode: NeuralGalaxyLayout, width: Float, height: Float, density: Float): GraphViewport? {
        if (mode == NeuralGalaxyLayout.BRAIN) return null
        // Canvas measurements can be zero or non-finite during transient layout states.
        // Keep alternate-layout hit testing finite without changing valid screen geometry.
        val safeWidth = width.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeHeight = height.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val side = minOf(12f*safeDensity, safeWidth*.1f)
        val top = minOf(64f*safeDensity, safeHeight*.26f)
        val bottom = minOf(48f*safeDensity, safeHeight*.22f)
        return GraphViewport(side, top, (safeWidth-side).coerceAtLeast(side+1f),
            (safeHeight-bottom).coerceAtLeast(top+1f))
    }

    fun matches(nodeId: String, label: String, group: String, area: String, query: String): Boolean =
        query.isBlank() || listOf(nodeId,label,group,area).any { it.contains(query.trim(), true) }

    fun safeRadius(value: Float): Float =
        if (value.isFinite() && value > 0f) value.coerceAtMost(64f) else 16f

    fun safeCoordinate(value: Float): Float =
        if (value.isFinite()) value.coerceIn(-10_000f, 10_000f) else 0f

    fun prepare(mode: NeuralGalaxyLayout, source: List<GalaxyNode>, edges: List<GalaxyEdge>,
        activity: List<GalaxyActivity> = emptyList()): GalaxyGeometry {
        require(source.map { it.id }.distinct().size == source.size) { "Duplicate graph node identity." }
        require(source.all { it.id.isNotBlank() })
        if (source.isEmpty()) return if (mode == NeuralGalaxyLayout.TIMELINE)
            timeline(emptyList(), activity) else GalaxyGeometry(emptyMap())
        val nodes = source.sortedBy { it.id }
        val valid = nodes.map { n -> n.copy(initial = SpacePoint(
            safeCoordinate(n.initial.x), safeCoordinate(n.initial.y), safeCoordinate(n.initial.z))) }
        return when (mode) {
            NeuralGalaxyLayout.BRAIN -> GalaxyGeometry(valid.associate { it.id to it.initial })
            NeuralGalaxyLayout.RINGS -> rings(valid)
            NeuralGalaxyLayout.CIRCLE -> GalaxyGeometry(valid.mapIndexed { i, n ->
                n.id to onCircle(i, valid.size, 150f)
            }.toMap())
            NeuralGalaxyLayout.AREAS -> areas(valid)
            NeuralGalaxyLayout.LINKS -> links(valid, edges)
            NeuralGalaxyLayout.TIMELINE -> timeline(valid, activity)
            NeuralGalaxyLayout.ORBIT -> GalaxyGeometry(valid.mapIndexed { i, n ->
                val y = 1f - 2f * (i + .5f) / valid.size
                val radial = sqrt((1f - y*y).coerceAtLeast(0f))
                val angle = i * 2.3999632f
                n.id to SpacePoint(cos(angle)*radial*145f, y*145f, sin(angle)*radial*145f)
            }.toMap())
        }
    }

    private fun onCircle(i: Int, count: Int, radius: Float): SpacePoint {
        val angle = (2.0 * PI * i / count.coerceAtLeast(1) - PI / 2).toFloat()
        return SpacePoint(cos(angle)*radius, sin(angle)*radius, 0f)
    }

    private fun rings(nodes: List<GalaxyNode>): GalaxyGeometry {
        val root = nodes.firstOrNull { it.id == "maximus" } ?: nodes.firstOrNull { it.layer == 0 }
        val groups = nodes.filter { it.id != root?.id }.groupBy { it.layer.coerceIn(1, 5) }.toSortedMap()
        val positions = linkedMapOf<String, SpacePoint>()
        root?.let { positions[it.id] = SpacePoint(0f,0f,0f) }
        // Preserve the canonical layer radius even when intermediate layers have no nodes.
        groups.forEach { (layer, members) ->
            members.forEachIndexed { i, node -> positions[node.id] = onCircle(i, members.size, 65f + (layer-1)*65f) }
        }
        return GalaxyGeometry(positions)
    }

    private fun areas(nodes: List<GalaxyNode>): GalaxyGeometry {
        val groups = nodes.groupBy { it.area }.toSortedMap()
        val columns = ceil(sqrt(groups.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(groups.size.toDouble()/columns).toInt()
        val labels = linkedMapOf<String, SpacePoint>()
        val positions = linkedMapOf<String, SpacePoint>()
        groups.entries.forEachIndexed { index, (area, members) ->
            val center = SpacePoint((index%columns-(columns-1)/2f)*160f,
                (index/columns-(rows-1)/2f)*100f,0f)
            labels[area] = center + SpacePoint(0f,-40f,0f)
            members.forEachIndexed { i, node ->
                positions[node.id] = if (members.size == 1) center else center + onCircle(i, members.size, 29f)
            }
        }
        return GalaxyGeometry(positions, labels)
    }

    private fun timeline(nodes: List<GalaxyNode>, records: List<GalaxyActivity>): GalaxyGeometry {
        // A persisted ID identifies a record, not a node/source label. Repeated events remain distinct.
        val activity = records.filter { it.id.isNotBlank() }.distinctBy { it.id }
            .sortedWith(compareBy<GalaxyActivity> { if(it.chronological) it.timestamp else Long.MIN_VALUE }
                .thenBy { it.id }).takeLast(100)
        val dated = nodes.filter { it.changedAt != null && it.changedAt > 0L }
        val timestamps = dated.map { requireNotNull(it.changedAt) } +
            activity.filter { it.chronological }.mapNotNull { it.timestamp }
        val first = timestamps.minOrNull()
        val last = timestamps.maxOrNull()
        val undated = nodes.filter { it.changedAt == null || it.changedAt <= 0L }
        val positions = linkedMapOf<String, SpacePoint>()
        dated.groupBy { requireNotNull(it.changedAt) }.toSortedMap().forEach { (time, simultaneous) ->
            val fraction = if (first == last) .5 else
                (time - requireNotNull(first)).toDouble() /
                (requireNotNull(last) - first).coerceAtLeast(1L).toDouble()
            simultaneous.forEachIndexed { i, node ->
                positions[node.id] = SpacePoint((fraction*280-140).toFloat(),
                    (if (node.runtimeObserved) -75f else 35f) + (i%5)*13f, 0f)
            }
        }
        undated.forEachIndexed { i, node ->
            positions[node.id] = SpacePoint((i%12-5.5f)*24f, 115f+(i/12)*13f,0f)
        }
        val activityPositions = linkedMapOf<String,SpacePoint>()
        val lanes = mutableMapOf<Long?,Int>()
        activity.forEach { record ->
            val time = record.timestamp?.takeIf { record.chronological }
            val track = lanes.getOrDefault(time,0)
            lanes[time] = track + 1
            val fraction = if (time == null || first == last) .5 else
                (time - requireNotNull(first)).toDouble() /
                    (requireNotNull(last)-first).coerceAtLeast(1L).toDouble()
            activityPositions[record.id] = if (time == null)
                SpacePoint((track%12-5.5f)*24f,-190f-(track/12)*12f,0f)
            else SpacePoint((fraction*280-140).toFloat(), -160f+(track%8)*10f,0f)
        }
        return GalaxyGeometry(positions, firstTimestamp = first, lastTimestamp = last,
            undatedCount = undated.size + activity.count { !it.chronological },
            activityPositions = activityPositions)
    }

    private fun links(nodes: List<GalaxyNode>, edges: List<GalaxyEdge>): GalaxyGeometry {
        val count = nodes.size
        val index = nodes.mapIndexed { i,n -> n.id to i }.toMap()
        val connections = edges.mapNotNull { e ->
            val from = index[e.source]; val to = index[e.target]
            // Links are visual undirected springs: reversed or repeated edges must not add force.
            if (from == null || to == null || from == to) null
            else minOf(from, to) to maxOf(from, to)
        }.distinct().sortedWith(compareBy<Pair<Int,Int>> { it.first }.thenBy { it.second })
        val x = FloatArray(count) { onCircle(it,count,120f).x }
        val y = FloatArray(count) { onCircle(it,count,120f).y }
        val dx = FloatArray(count); val dy = FloatArray(count)
        repeat(72) { step ->
            dx.fill(0f); dy.fill(0f)
            for (i in 0 until count) {
                // At most 64 deterministic samples per node, including large imported graphs.
                val sampleCount = minOf(count-1,64)
                for (sample in 0 until sampleCount) {
                    val j = if (count <= 65) (i+sample+1)%count
                        else (i+1+(sample.toLong()*(count-1)/sampleCount).toInt())%count
                    var vx = x[i]-x[j]; var vy = y[i]-y[j]
                    if (abs(vx)+abs(vy)<.001f) { vx=.1f; vy=.1f }
                    val distance2 = (vx*vx+vy*vy).coerceAtLeast(25f)
                    val repulsion = 145f/distance2
                    dx[i] += vx*repulsion; dy[i] += vy*repulsion
                }
            }
            for ((from,to) in connections) {
                val vx=x[to]-x[from]; val vy=y[to]-y[from]
                val distance=sqrt(vx*vx+vy*vy).coerceAtLeast(1f)
                val force=(distance-43f)*.024f
                dx[from]+=vx/distance*force; dy[from]+=vy/distance*force
                dx[to]-=vx/distance*force; dy[to]-=vy/distance*force
            }
            val temperature=4f*(1f-step/90f)
            for(i in 0 until count) {
                x[i]=(x[i]+(dx[i]-.008f*x[i]).coerceIn(-temperature,temperature)).coerceIn(-185f,185f)
                y[i]=(y[i]+(dy[i]-.008f*y[i]).coerceIn(-temperature,temperature)).coerceIn(-185f,185f)
            }
        }
        return GalaxyGeometry(nodes.mapIndexed { i,n -> n.id to SpacePoint(x[i],y[i],0f) }.toMap())
    }
}
