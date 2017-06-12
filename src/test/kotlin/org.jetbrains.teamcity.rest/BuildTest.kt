package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BuildTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_to_string() {
        val builds = compileExamplesConfigurationBuilds()
                .limitResults(3)
                .list()

        println(builds.joinToString("\n"))
    }

    @Test
    fun test_build_fetch_revisions() {
        compileExamplesConfigurationBuilds()
                .limitResults(10)
                .list()
                .forEach {
                    val revisions = it.fetchRevisions()
                    Assert.assertTrue(revisions.isNotEmpty())
                }
    }

    @Test
    fun test_fetch_status() {
        val build = compileExamplesConfigurationBuilds()
                .limitResults(1)
                .list().first()

        build.fetchStatusText()
    }

    @Test
    fun test_failed_to_start_build() {
        fun getBuildWithFailedToStart(failedToStart: Boolean?): Build? {
            val locator = compileExamplesConfigurationBuilds().limitResults(1)

            if (failedToStart != null) {
                locator.withFailedToStart(failedToStart)
            } else {
                locator.withAnyFailedToStart()
            }

            return locator.list().firstOrNull()
        }

        val failedToStartBuild = getBuildWithFailedToStart(true)
        println("Failed to start: $failedToStartBuild")
        if (failedToStartBuild != null) {
            Assert.assertEquals("Bad failedToStart value", true, failedToStartBuild.failedToStart)
        }

        val normaBuild = getBuildWithFailedToStart(false)
        println("Normal build: $normaBuild")
        if (normaBuild != null) {
            Assert.assertEquals("Bad failedToStart value", false, normaBuild.failedToStart)
        }

        val anyBuild = getBuildWithFailedToStart(null)
        println("First build: $anyBuild")
        if (failedToStartBuild != null || normaBuild != null) {
            Assert.assertNotNull(anyBuild)

            val failedToStartId = failedToStartBuild?.id?.stringId?.toInt() ?: 0
            val normalId = normaBuild?.id?.stringId?.toInt() ?: 0
            val maxId = Math.max(failedToStartId, normalId)

            val anyId = anyBuild!!.id.stringId.toInt()

            Assert.assertEquals("Wrong build id for query  with any failedToStart value", maxId, anyId)
        }
    }

    @Test
    fun test_since_build() {
        val builds = compileExamplesConfigurationBuilds()
                .withAnyStatus()
                .limitResults(2)
                .list()

        Assert.assertEquals("Two builds expected", 2, builds.size)

        val newer = builds[0]
        val olderBuild = builds[1]

        run {
            val sinceLocatorById = publicInstance().builds().withAnyStatus().withId(olderBuild.id)
            val newerWithSinceById = compileExamplesConfigurationBuilds()
                    .withAnyStatus()
                    .withSinceBuild(sinceLocatorById)
                    .list().last()
            Assert.assertEquals("Should be same build on fetching with since locator",
                    newer.id, newerWithSinceById.id)
        }

        run {
            // NOTE: Configuration is mandatory in since locator
            val sinceLocatorByNumber = compileExamplesConfigurationBuilds().withAnyStatus().withNumber(olderBuild.buildNumber)
            val newerWithSinceByNumber = publicInstance().builds()
                    .fromConfiguration(compileExamplesConfiguration)
                    .withAnyStatus()
                    .withSinceBuild(sinceLocatorByNumber)
                    .list().last()
            Assert.assertEquals("Should be same build on fetching with since locator",
                    newer.id, newerWithSinceByNumber.id)
        }
    }

    @Test
    fun test_fetch_dates() {
        val build = compileExamplesConfigurationBuilds()
                .withAnyStatus()
                .limitResults(1)
                .latest()!!

        val queuedDate = build.fetchQueuedDate()
        val startDate = build.fetchStartDate()
        val finishDate = build.fetchFinishDate()

        Assert.assertTrue(startDate > queuedDate)
        Assert.assertTrue(finishDate > startDate)
    }

    @Test
    fun test_after_finish_date_query() {
        val builds = compileExamplesConfigurationBuilds()
                .withAnyStatus()
                .limitResults(2)
                .list()

        Assert.assertEquals("Two builds expected", 2, builds.size)

        val newer = builds[0]
        val older = builds[1]

        if (newer.fetchFinishDate() <= older.fetchFinishDate()) {
            // In general this condition is false, but it might happen that more recent build had
            // finished before the older one
            return
        }

        run {
            val afterOlderById = compileExamplesConfigurationBuilds()
                    .withAnyStatus()
                    .withFinishDateQuery(
                            afterBuildQuery(publicInstance().builds().withAnyStatus().withId(older.id))
                    )
                    .list().last()

            Assert.assertEquals("After query by id should fetch same newer build", newer.id, afterOlderById.id)
        }

        run {
            val afterOlderByDate = compileExamplesConfigurationBuilds()
                    .withAnyStatus()
                    .withFinishDateQuery(afterDateQuery(older.fetchFinishDate()))
                    .list().last()

            Assert.assertEquals("After query by date should fetch same newer build", newer.id, afterOlderByDate.id)
        }
    }

    @Test
    fun test_before_start_date_query() {
        val builds = compileExamplesConfigurationBuilds()
                .withAnyStatus()
                .limitResults(2)
                .list()

        Assert.assertEquals("Two builds expected", 2, builds.size)

        val newer = builds[0]
        val older = builds[1]

        run {
            val beforeNewerById = compileExamplesConfigurationBuilds()
                    .withAnyStatus()
                    .withStartDateQuery(
                            beforeBuildQuery(publicInstance().builds().withAnyStatus().withId(newer.id))
                    )
                    .limitResults(1)
                    .latest()!!

            Assert.assertEquals("Before query by id should fetch same older build", older.id, beforeNewerById.id)
        }

        run {
            val beforeNewerByDate = compileExamplesConfigurationBuilds()
                    .withAnyStatus()
                    .withStartDateQuery(beforeDateQuery(newer.fetchStartDate()))
                    .limitResults(1)
                    .latest()!!

            Assert.assertEquals("Before query by date should fetch same older build", older.id, beforeNewerByDate.id)
        }
    }

    private fun compileExamplesConfigurationBuilds() =
            publicInstance().builds().fromConfiguration(compileExamplesConfiguration)
}
