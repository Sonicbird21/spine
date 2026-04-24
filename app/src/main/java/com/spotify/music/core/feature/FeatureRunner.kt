package com.spotify.music.core.feature

import java.util.ArrayDeque

data class FeatureInstallReport(
    val applied: Set<String>,
    val failed: Map<String, Throwable>,
)

class FeatureRunner(
    private val features: List<HookFeature>,
) {
    fun installAll(context: FeatureContext): FeatureInstallReport {
        val featureMap = features.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val graph = mutableMapOf<String, MutableSet<String>>()
        val failures = linkedMapOf<String, Throwable>()
        val applied = linkedSetOf<String>()

        features.forEach { feature ->
            inDegree.putIfAbsent(feature.id, 0)
            graph.putIfAbsent(feature.id, linkedSetOf())
        }

        features.forEach { feature ->
            feature.dependsOn.forEach { dep ->
                if (!featureMap.containsKey(dep)) {
                    failures[feature.id] = IllegalStateException(
                        "Missing dependency '$dep' for feature '${feature.id}'"
                    )
                    return@forEach
                }
                graph.getValue(dep).add(feature.id)
                inDegree[feature.id] = inDegree.getValue(feature.id) + 1
            }
        }

        val queue = ArrayDeque<String>()
        inDegree.filterValues { it == 0 }.keys.forEach(queue::add)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            if (failures.containsKey(currentId)) {
                graph[currentId].orEmpty().forEach { nextId ->
                    inDegree[nextId] = inDegree.getValue(nextId) - 1
                    if (inDegree.getValue(nextId) == 0) queue.add(nextId)
                }
                continue
            }

            val feature = featureMap.getValue(currentId)
            val hasFailedDependency = feature.dependsOn.any { dep -> failures.containsKey(dep) }
            if (hasFailedDependency) {
                failures[currentId] = IllegalStateException(
                    "Dependency failure prevents feature '${feature.id}' installation"
                )
            } else {
                runCatching {
                    feature.install(context)
                }.onSuccess {
                    applied += currentId
                }.onFailure { err ->
                    failures[currentId] = err
                }
            }

            graph[currentId].orEmpty().forEach { nextId ->
                inDegree[nextId] = inDegree.getValue(nextId) - 1
                if (inDegree.getValue(nextId) == 0) queue.add(nextId)
            }
        }

        inDegree.filterValues { it > 0 }.keys.forEach { pendingId ->
            failures.putIfAbsent(
                pendingId,
                IllegalStateException("Dependency cycle detected for feature '$pendingId'")
            )
        }

        return FeatureInstallReport(applied = applied, failed = failures)
    }
}
