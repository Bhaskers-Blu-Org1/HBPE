/*
Copyright IBM Corporation 2020. All Rights Reserved
SPDX-License-Identifier: Apache-2.0

Description : Unit Tests for Histogram-based percentile estimator
Author      : David Ohana (david.ohana@ibm.com)
*/

package com.ibm.hbpe

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInRange
import org.amshove.kluent.shouldBeNear
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.junit.Test
import java.util.*


class HbpeTest {
    @Test
    fun testFloorResolution() {
        10.1.floorResolution(1).shouldBeEqualTo(10.1)
        2.0.floorResolution(0).shouldBeEqualTo(2.0)
        0.22.floorResolution(0).shouldBeEqualTo(0.0)
        0.22.floorResolution(1).shouldBeEqualTo(0.2)
        0.29.floorResolution(1).shouldBeEqualTo(0.2)
        0.0.floorResolution(1).shouldBeEqualTo(0.0)
        0.298.floorResolution(2).shouldBeEqualTo(0.29)
    }

    @Test
    fun testFloorResolutionRandom() {
        val rnd = Random(2)
        for (i in 0..100000) {
            val v = 15 + 0.1 + rnd.nextDouble() * 0.1
            val floored = v.floorResolution(1)
            floored.shouldBeEqualTo(15.1)
        }
    }

    @Test
    fun testAddManyValuesAndGetRanks() {
        val rnd = Random(1)
        for (i in 0..2) {
            val hbpe = HistogramBasedPercentileEstimator(1)
            hbpe.getRankThenAdd(0.0)
            for (j in 0..300) {
                val v = rnd.nextDouble() * 100000
                val pr = hbpe.getRankThenAdd(v)
                pr.shouldBeInRange(0.0, 100.0)
            }
        }
    }

    /**
     * regression test of get percentile by comparing to math3 implementation as a reference
     */
    @Test
    fun testGetPercentileVsRefImpl() {
        val population = mutableListOf<Double>()

        val refImpl = PercentileInclusive()
        val hbpe = HistogramBasedPercentileEstimator(1)

        fun assertResult(p: Double) {
            val refResult = refImpl.evaluate(population.toDoubleArray(), p)
            val hbpeResult = hbpe.getPercentile(p)
            hbpeResult.shouldBeNear(refResult, hbpe.bucketSize)
        }

        fun addAndAssert(v: Double) {
            population.add(v)
            hbpe.addValue(v)
            assertResult(100.0)
            assertResult(99.9)
            assertResult(99.0)
            assertResult(95.0)
            assertResult(80.0)
            assertResult(75.0)
            assertResult(66.0)
            assertResult(50.0)
            assertResult(33.0)
            assertResult(25.0)
            assertResult(20.0)
            assertResult(10.0)
            assertResult(5.0)
            assertResult(1.0)
            assertResult(0.1)
        }

        addAndAssert(10.0)
        addAndAssert(20.0)
        addAndAssert(30.0)
        addAndAssert(40.0)
        addAndAssert(0.0)
        addAndAssert(50.0)
        addAndAssert(0.0)
        addAndAssert(100.0)
        addAndAssert(29.0)
        addAndAssert(99.0)
        addAndAssert(100.0)
        addAndAssert(1.0)
        addAndAssert(-1.0)
        addAndAssert(3.0)
        addAndAssert(3.0)
        addAndAssert(30.0)
        addAndAssert(100.0)
        addAndAssert(100.0)
        addAndAssert(1000.0)
        addAndAssert(-1000.0)
        addAndAssert(0.0)
    }

    @Test
    fun testGetPercentileVsRefImplRandom() {
        for (scale in 0..5) {
            println("Testing Percentile at scale $scale")
            compareGetPercentileToRefImplRandom(scale)
        }
    }

    fun compareGetPercentileToRefImplRandom(scale: Int) {
        val population = mutableListOf<Double>()

        val refImpl = PercentileInclusive()
        val hbpe = HistogramBasedPercentileEstimator(scale)

        fun assertResult(p: Double) {
            val refResult = refImpl.evaluate(population.toDoubleArray(), p)
            val hbpeResult = hbpe.getPercentile(p)
            hbpeResult.shouldBeNear(refResult, hbpe.bucketSize)
        }

        fun addAndAssert(v: Double) {
            population.add(v)
            hbpe.addValue(v)
            assertResult(100.0)
            assertResult(99.9)
            assertResult(99.0)
            assertResult(95.0)
            assertResult(80.0)
            assertResult(75.0)
            assertResult(66.0)
            assertResult(50.0)
            assertResult(33.0)
            assertResult(25.0)
            assertResult(20.0)
            assertResult(10.0)
            assertResult(5.0)
            assertResult(1.0)
            assertResult(0.1)
        }

        val rnd = Random(3)
        fun getRandomValue(): Double {
            val maxVal = 10000 * hbpe.bucketSize
            return (rnd.nextDouble() * 2.0 - 1) * maxVal
        }

        for (i in 1..2000) {
            val v = getRandomValue()
            addAndAssert(v)
        }
    }

    /*
        Compare get percentile rank to reference naive (unoptimized) implementation
    */
    @Test
    fun testGetPercentileRank() {
        for (scale in 0..5) {
            println("Testing PR at scale $scale")
            compareGetPercentileRankToNaive(scale)
        }
    }

    fun compareGetPercentileRankToNaive(scale: Int) {
        val population = mutableListOf<Double>()
        val hbpe = HistogramBasedPercentileEstimator(scale)

        fun addValue(v: Double) {
            population.add(v)
            hbpe.addValue(v)
        }

        fun assertAndAdd(v: Double) {
            val refPr = calculateSimplePercentileRank(population, v)
            val hbpePr = hbpe.getPercentileRank(v)
            hbpePr.shouldBeEqualTo(refPr)
            //hbpePr.shouldBeNear(refPr, 0.1)
            addValue(v)
        }

        addValue(10.0)

        // start with some predetermined values for sanity check
        assertAndAdd(40.0)
        assertAndAdd(20.0)
        assertAndAdd(30.0)
        assertAndAdd(30.0)

        val rnd = Random(4)
        fun getRandomValue(): Double {
            val maxVal = 100000 * hbpe.bucketSize
            return (rnd.nextDouble() * 2.0 - 1) * maxVal
        }

        for (i in 1..1000) {
            val v = getRandomValue()
            // floor value according to scale in order to make sure there are no diffs
            // because of accuracy loss
            val floored = v.floorResolution(scale)
            assertAndAdd(floored)
        }
    }

    /*
    Naive implementation of PR. Formula according to https://en.wikipedia.org/wiki/Percentile_rank
     */
    fun calculateSimplePercentileRank(population: List<Double>, rankOf: Double): Double {

        val countLess = population.count { it < rankOf }
        val countEqual = population.count { it == rankOf }
        return (countLess + 0.5 * countEqual) / population.size * 100
    }

    @Test
    fun comparePerformanceToMath3() {
        val rnd = Random(5)
        val refImpl = DescriptiveStatistics()
        val hbpe = HistogramBasedPercentileEstimator(1)

        fun singleRunRefImpl(v: Double) {
            refImpl.addValue(v)
            refImpl.getPercentile(100.0)
            refImpl.getPercentile(99.0)
            refImpl.getPercentile(75.0)
            refImpl.getPercentile(50.0)
            refImpl.getPercentile(25.0)
            refImpl.getPercentile(1.0)
        }

        fun singleRunHbpe(v: Double) {
            hbpe.addValue(v)
            hbpe.getPercentile(100.0)
            hbpe.getPercentile(99.0)
            hbpe.getPercentile(75.0)
            hbpe.getPercentile(50.0)
            hbpe.getPercentile(25.0)
            hbpe.getPercentile(1.0)
        }

        fun benchmark(name: String, singleRun: (Double) -> Unit) {
            val startTimeMs = System.currentTimeMillis()
            for (i in 1..10000) {
                val v = rnd.nextDouble() * 200 - 300
                singleRun(v)
            }
            val tookSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
            println("$name took: $tookSec sec")
        }

        benchmark("math3-cold", ::singleRunRefImpl)
        refImpl.clear()
        benchmark("math3-warm", ::singleRunRefImpl)

        benchmark("hbpe-cold", ::singleRunHbpe)
        hbpe.clear()
        benchmark("hbpe-warm", ::singleRunHbpe)
    }


    @Test
    fun testCopyConstructor() {
        fun assertEqual(hbpe1: HistogramBasedPercentileEstimator, hbpe2: HistogramBasedPercentileEstimator) {
            hbpe2.lowBoundInclusive.shouldBeEqualTo(hbpe1.lowBoundInclusive)
            hbpe2.higBoundExclusive.shouldBeEqualTo(hbpe1.higBoundExclusive)
            hbpe2.precisionScale.shouldBeEqualTo(hbpe1.precisionScale)
            hbpe2.bucketSize.shouldBeEqualTo(hbpe1.bucketSize)
            hbpe2.valueCount.shouldBeEqualTo(hbpe1.valueCount)
            hbpe2.bucketsValueCount.shouldBeEqualTo(hbpe1.bucketsValueCount)
            hbpe2.bucketsHighBound.shouldBeEqualTo(hbpe1.bucketsHighBound)
        }

        val hbpe1 = HistogramBasedPercentileEstimator(3)
        val hbpe2 = HistogramBasedPercentileEstimator(hbpe1)

        assertEqual(hbpe1, hbpe2)

        hbpe1.addValue(30.0)
        hbpe1.addValue(15.0)
        hbpe1.addValue(150.0)

        val hbpe3 = HistogramBasedPercentileEstimator(hbpe1)
        assertEqual(hbpe1, hbpe3)
    }

    @Test
    fun testGetValueRange() {
        val hbpe = HistogramBasedPercentileEstimator(1)
        hbpe.getBucketCount().shouldBeEqualTo(0)
        hbpe.getValueRange().shouldBeEqualTo(Double.NaN)

        hbpe.addValue(2.0)
        hbpe.getBucketCount().shouldBeEqualTo(1)
        hbpe.getValueRange().shouldBeEqualTo(0.1)

        hbpe.addValue(2.0)
        hbpe.getBucketCount().shouldBeEqualTo(1)
        hbpe.getValueRange().shouldBeEqualTo(0.1)

        hbpe.addValue(3.0)
        hbpe.getBucketCount().shouldBeEqualTo(11)
        hbpe.getValueRange().shouldBeEqualTo(1.1)
    }

    @Test
    fun testAddManyValues() {
        val hbpe = HistogramBasedPercentileEstimator(1)
        for (i in 0..10000000) {
            val height = Math.random() * 150 + 50
            hbpe.addValue(height)
        }
    }
}

