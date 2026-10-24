package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test

class NeuralGalaxyStage2Test {
    @Test fun allSixAlternateModesReserveTheSameToolbarSafeProjectionAndHitRegion() {
        for (mode in NeuralGalaxyLayout.entries.filter { it != NeuralGalaxyLayout.BRAIN }) {
            val viewport = requireNotNull(NeuralGalaxyLayouts.viewport(mode,1080f,1200f,3f))
            assertEquals(192f,viewport.top,0f)
            val projection = projectGraph(SpacePoint(0f,0f,0f),GraphRotation(),1080f,1200f,200f,1f,viewport)
            assertTrue(viewport.contains(projection.x,projection.y))
            assertFalse(viewport.contains(projection.x,20f))
            assertEquals((viewport.top+viewport.bottom)/2f,projection.y,0f)
        }
    }

    @Test fun brainDefaultProjectionAndCameraAreUnchanged() {
        assertNull(NeuralGalaxyLayouts.viewport(NeuralGalaxyLayout.BRAIN,360f,640f,1f))
        val point=SpacePoint(20f,60f,30f)
        val rotation=GraphRotation.defaultBrainView()
        assertEquals(projectGraph(point,rotation,360f,640f,200f,1f),
            projectGraph(point,rotation,360f,640f,200f,1f,
                NeuralGalaxyLayouts.viewport(NeuralGalaxyLayout.BRAIN,360f,640f,1f)))
    }

    @Test fun constrainedPortraitAndLandscapeViewportsRemainPositive() {
        for ((w,h) in listOf(360f to 70f,70f to 360f,840f to 240f)) {
            val viewport=requireNotNull(NeuralGalaxyLayouts.viewport(NeuralGalaxyLayout.AREAS,w,h,3f))
            assertTrue(viewport.width>0f && viewport.height>0f)
            assertTrue(viewport.left>=0f && viewport.top>=0f && viewport.right<=w && viewport.bottom<=h)
        }
    }

    @Test fun measuredAreaLabelBoxesNeverOverlapOrEscapeTheUsableCanvas() {
        val viewport=GraphViewport(12f,64f,348f,500f)
        val occupied=mutableListOf<GraphLabelBox>()
        for (i in 0 until 9) {
            val box=placeGraphLabel("area-$i",60f+(i%3)*110f,100f+(i/3)*90f,
                105f,32f,viewport,occupied,3f)
            assertNotNull(box)
            val placed=requireNotNull(box)
            assertTrue(placed.left>=viewport.left && placed.right<=viewport.right)
            assertTrue(placed.top>=viewport.top && placed.bottom<=viewport.bottom)
            assertTrue(occupied.none { placed.overlaps(it,3f) })
            occupied += placed
        }
    }

    @Test fun crowdedLabelsAreOmittedInsteadOfOverpaintingEachOther() {
        val viewport=GraphViewport(0f,0f,80f,30f)
        val existing=GraphLabelBox("one",0f,0f,80f,30f)
        assertNull(placeGraphLabel("two",40f,0f,80f,30f,viewport,listOf(existing),2f))
    }

    @Test fun timelineKeepsRepeatedEventsDistinctAndUsesTheirPersistedTimes() {
        val events=listOf(
            GalaxyActivity("one",1000L,"agent","project","RUNNING","Started"),
            GalaxyActivity("two",1500L,"agent","project","FAILED","Failed"),
            GalaxyActivity("three",1500L,"agent","project","RUNNING","Another actual record"))
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),events)
        assertEquals(setOf("one","two","three"),result.activityPositions.keys)
        assertEquals(1000L,result.firstTimestamp)
        assertEquals(1500L,result.lastTimestamp)
        assertTrue(result.activityPositions.getValue("one").x < result.activityPositions.getValue("two").x)
        assertNotEquals(result.activityPositions["two"],result.activityPositions["three"])
        assertEquals(result,NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),events.reversed()))
    }

    @Test fun conflictingDuplicateActivityIdsHaveStableTimelineEvidence() {
        val older = GalaxyActivity("shared",1000L,"agent","project","RUNNING","Earlier")
        val newer = older.copy(timestamp=2000L, type="COMPLETED", summary="Later")
        val separate = GalaxyActivity("other",1500L,"agent","project","INFO","Distinct event")
        val records = listOf(older, separate, newer)
        val forward = NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),records)
        val reverse = NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),records.reversed())
        assertEquals(forward, reverse)
        assertEquals(setOf("shared","other"), forward.activityPositions.keys)
        assertEquals(1500L, forward.firstTimestamp)
        assertEquals(2000L, forward.lastTimestamp)
        assertEquals(0, forward.undatedCount)
    }

    @Test fun futureAndInvalidRecordedTimesAreFlaggedAndNeverBecomeChronology() {
        val raw = GalaxyActivity("future",5000L,"source",null,"COMPLETED","Not independently verified")
        val checked = NeuralGalaxyLayouts.clockChecked(raw,1000L)
        assertEquals(5000L,checked.timestamp)
        assertEquals(GalaxyTimestampIssue.FUTURE,checked.timestampIssue)
        val invalid=NeuralGalaxyLayouts.clockChecked(raw.copy(id="invalid",timestamp=-10L),1000L)
        assertEquals(GalaxyTimestampIssue.INVALID,invalid.timestampIssue)
        val result=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),listOf(checked,invalid))
        assertNull(result.firstTimestamp); assertNull(result.lastTimestamp)
        assertEquals(2,result.undatedCount)
        assertEquals(setOf("future","invalid"),result.activityPositions.keys)
    }

    @Test fun eventOverlayDoesNotReplaceCanonicalGraphIdentities() {
        val nodes=listOf(GalaxyNode("root","Core",0,SpacePoint(0f,0f,0f)))
        val event=GalaxyActivity("event",1000L,"root",null,"INFO","Recorded evidence")
        for(mode in NeuralGalaxyLayout.entries) {
            val result=NeuralGalaxyLayouts.prepare(mode,nodes,emptyList(),listOf(event))
            assertEquals(setOf("root"),result.positions.keys)
            assertEquals(if(mode==NeuralGalaxyLayout.TIMELINE) setOf("event") else emptySet<String>(),
                result.activityPositions.keys)
        }
    }

    @Test fun undatedEventsRemainUndatedAndHistoryProjectionIsBounded() {
        val unknown=GalaxyActivity("undated",null,"source",null,"UNKNOWN","Missing date")
        val empty=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),listOf(unknown))
        assertNull(empty.firstTimestamp); assertNull(empty.lastTimestamp)
        assertEquals(1,empty.undatedCount)
        val records=(1..125).map { GalaxyActivity("event-$it",it.toLong(),"source",null,"INFO","") }
        val bounded=NeuralGalaxyLayouts.prepare(NeuralGalaxyLayout.TIMELINE,emptyList(),emptyList(),records)
        assertEquals(100,bounded.activityPositions.size)
        assertFalse("event-1" in bounded.activityPositions)
        assertTrue("event-125" in bounded.activityPositions)
    }

    @Test fun commonSearchMatchesStableIdentityAreaAndGroupWithoutChangingFacts() {
        assertTrue(NeuralGalaxyLayouts.matches("project-42","Chart","Pine","Trading & Pine","42"))
        assertTrue(NeuralGalaxyLayouts.matches("project-42","Chart","Pine","Trading & Pine","trading"))
        assertTrue(NeuralGalaxyLayouts.matches("project-42","Chart","Pine","Trading & Pine","CHART"))
        assertFalse(NeuralGalaxyLayouts.matches("project-42","Chart","Pine","Trading & Pine","unrelated"))
    }
}
