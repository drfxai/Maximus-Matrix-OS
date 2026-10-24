package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class NeuralGalaxyLayoutsTest {
    private val nodes = listOf(
        GalaxyNode("maximus","AI Core",0,SpacePoint(2f,3f,4f)),
        GalaxyNode("model","Smart Combo",1,SpacePoint(30f,50f,7f),1000L,true),
        GalaxyNode("memory","Hindsight Memory",1,SpacePoint(-30f,50f,-7f),2000L),
        GalaxyNode("project","Projects",2,SpacePoint(0f,-50f,0f))
    )
    private val edges = listOf(GalaxyEdge("maximus","model"),GalaxyEdge("model","memory"),
        GalaxyEdge("memory","project"))

    @Test fun allViewsRetainTheSameCanonicalIdentitiesAndFinitePositions() {
        for (layout in NeuralGalaxyLayout.entries) {
            val geometry=NeuralGalaxyLayouts.prepare(layout,nodes,edges)
            assertEquals(nodes.map { it.id }.toSet(),geometry.positions.keys)
            assertTrue(geometry.positions.values.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
        }
    }

    @Test fun savedGeometryIsBoundedWithoutRewritingSourceRecords() {
        assertEquals(16f,NeuralGalaxyLayouts.safeRadius(Float.NaN),0f)
        assertEquals(16f,NeuralGalaxyLayouts.safeRadius(-5f),0f)
        assertEquals(64f,NeuralGalaxyLayouts.safeRadius(Float.MAX_VALUE),0f)
        assertEquals(4f,NeuralGalaxyLayouts.safeRadius(4f),0f)
        assertEquals(0f,NeuralGalaxyLayouts.safeCoordinate(Float.POSITIVE_INFINITY),0f)
        assertEquals(10_000f,NeuralGalaxyLayouts.safeCoordinate(Float.MAX_VALUE),0f)
    }

    @Test fun alternateLayoutViewportRejectsInvalidTransientMeasurements() {
        assertNull(NeuralGalaxyLayouts.viewport(NeuralGalaxyLayout.BRAIN, Float.NaN, 0f, 0f))
        val normal = requireNotNull(NeuralGalaxyLayouts.viewport(
            NeuralGalaxyLayout.RINGS, 360f, 640f, 2f))
        assertEquals(24f, normal.left, 0f)
        assertEquals(128f, normal.top, 0f)
        assertEquals(336f, normal.right, 0f)
        assertEquals(544f, normal.bottom, 0f)

        val invalid = listOf(
            Triple(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN),
            Triple(-100f, 0f, -1f),
            Triple(Float.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY)
        )
        for (layout in NeuralGalaxyLayout.entries.filter { it != NeuralGalaxyLayout.BRAIN }) {
            for ((width, height, density) in invalid) {
                val viewport = requireNotNull(NeuralGalaxyLayouts.viewport(layout, width, height, density))
                assertTrue(listOf(viewport.left, viewport.top, viewport.right, viewport.bottom)
                    .all { it.isFinite() })
                assertTrue(viewport.width >= 1f && viewport.height >= 1f)
            }
        }
    }

    @Test fun originalBrainCoordinatesArePreserved() {
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.BRAIN,nodes,edges)
        nodes.forEach { assertEquals(it.initial,result.positions[it.id]) }
    }

    @Test fun layoutDoesNotDependOnRepositoryIterationOrder() {
        for(layout in NeuralGalaxyLayout.entries) {
            assertEquals(NeuralGalaxyLayouts.prepare(layout,nodes,edges),
                NeuralGalaxyLayouts.prepare(layout,nodes.reversed(),edges.reversed()))
        }
    }

    @Test fun circleHasAConstantRadiusAndRingsKeepBrainCentral() {
        val circle=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.CIRCLE,nodes,edges)
        circle.positions.values.forEach { assertEquals(150f,it.length,.001f) }
        val rings=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.RINGS,nodes,edges)
        assertEquals(SpacePoint(0f,0f,0f),rings.positions["maximus"])
        assertTrue(rings.positions.getValue("project").length > rings.positions.getValue("model").length)
    }

    @Test fun sparseRingsKeepCanonicalLayerDistances() {
        val sparse = listOf(
            GalaxyNode("maximus","AI Core",0,SpacePoint(0f,0f,0f)),
            GalaxyNode("middle","Work",3,SpacePoint(0f,0f,0f)),
            GalaxyNode("outer","Infrastructure",5,SpacePoint(0f,0f,0f))
        )
        val result = NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.RINGS,sparse,emptyList())
        assertEquals(0f,result.positions.getValue("maximus").length,.001f)
        assertEquals(195f,result.positions.getValue("middle").length,.001f)
        assertEquals(325f,result.positions.getValue("outer").length,.001f)
        assertEquals(result,NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.RINGS,sparse.reversed(),emptyList()))
    }

    @Test fun areasExposeOnlyDomainsPresentInTheCanonicalInput() {
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.AREAS,nodes,edges)
        assertEquals(nodes.map { it.area }.toSet(),result.areaLabels.keys)
        assertEquals(result.areaLabels.size,result.areaLabels.values.toSet().size)
    }

    @Test fun timelineUsesActualDatesAndSeparatesUndatedDefinitions() {
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,nodes,edges)
        assertEquals(1000L,result.firstTimestamp)
        assertEquals(2000L,result.lastTimestamp)
        assertEquals(2,result.undatedCount)
        assertTrue(result.positions.getValue("model").x < result.positions.getValue("memory").x)
        assertTrue(result.positions.getValue("model").y < result.positions.getValue("memory").y)
        assertTrue(result.positions.getValue("project").y >= 115f)
    }

    @Test fun extremeRecordedDatesDoNotOverflowOrCollapseToNaN() {
        val distant=nodes.mapIndexed { index,node ->
            node.copy(changedAt=Long.MAX_VALUE-10L+index)
        }
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,distant,edges)
        assertTrue(result.positions.values.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(result.positions.getValue("maximus").x < result.positions.getValue("project").x)
    }

    @Test fun missingAndInvalidDatesNeverBecomeSyntheticActivity() {
        val undated=nodes.map { it.copy(changedAt = -1L) }
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,undated,edges)
        assertNull(result.firstTimestamp); assertNull(result.lastTimestamp)
        assertEquals(nodes.size,result.undatedCount)
    }

    @Test fun emptyAndCorruptCoordinatesAreHandledWithoutInventingNodes() {
        for(layout in NeuralGalaxyLayout.entries) {
            assertTrue(NeuralGalaxyLayouts.prepare(layout,emptyList(),edges).positions.isEmpty())
            val corrupt=nodes.map { it.copy(initial=SpacePoint(Float.NaN,Float.POSITIVE_INFINITY,0f)) }
            assertEquals(nodes.size,NeuralGalaxyLayouts.prepare(layout,corrupt,edges).positions.size)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.LINKS,nodes+nodes.first(),edges)
        }
    }

    @Test fun linksIgnoreReversedAndRepeatedUndirectedEdges() {
        val baseline = NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.LINKS, nodes, edges)
        val repeated = edges + edges.map { GalaxyEdge(it.target, it.source) } + edges.first()
        assertEquals(baseline, NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.LINKS, nodes, repeated))
    }

    @Test fun forcePreparationIsBoundedForLargeGraphsAndIgnoresDanglingEdges() {
        val large=(0 until 513).map {
            GalaxyNode("node-"+it,"Projects",1,SpacePoint(0f,0f,0f))
        }
        val links=(1 until 513).map { GalaxyEdge("node-0","node-"+it) }+
            GalaxyEdge("missing","node-1")
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.LINKS,large,links)
        assertEquals(513,result.positions.size)
        result.positions.values.forEach {
            assertTrue(it.x in -185f..185f && it.y in -185f..185f && it.z == 0f)
        }
    }
}
