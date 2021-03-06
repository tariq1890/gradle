/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.cache;

public class HeapProportionalSizer {
    private static final int DEFAULT_SIZES_MAX_HEAP_MB = 910; // when -Xmx1024m, Runtime.maxMemory() returns about 910
    private static final int ASSUMED_USED_HEAP = 150; // assume that Gradle itself uses about 150MB heap

    private static final double MIN_RATIO = 0.2d;

    private final int maxHeapMB;
    private final double sizingRatio;

    public HeapProportionalSizer(int maxHeapMB) {
        this.maxHeapMB = maxHeapMB;
        this.sizingRatio = calculateRatio();
    }

    public HeapProportionalSizer() {
        this(calculateMaxHeapMB());
    }

    private static int calculateMaxHeapMB() {
        return (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    private double calculateRatio() {
        return Math.max((double) (maxHeapMB - ASSUMED_USED_HEAP) / (double) (DEFAULT_SIZES_MAX_HEAP_MB - ASSUMED_USED_HEAP), MIN_RATIO);
    }

    public int scaleValue(int referenceValue) {
        return scaleValue(referenceValue, 100);
    }

    public int scaleValue(int referenceValue, int granularity) {
        if (referenceValue < granularity) {
            throw new IllegalArgumentException("reference value must be larger than granularity");
        }
        return (int) ((double) referenceValue * sizingRatio) / granularity * granularity;
    }
}
