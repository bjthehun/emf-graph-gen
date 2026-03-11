/**
 * Copyright 2024 Karl Kegel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import deltamodel.*
import ecore.EObjectInventor
import ecore.EcoreHandler
import graphmodel.*
import util.Configuration
import util.GraphStats
import util.ImpactType
import util.Stage
import java.util.*
import kotlin.random.Random

/**
 * The GraphProcessor edits a [Graph] according to a configuration and
 *
 * The GraphProcessor is a 1-time use implementation.
 * After the exec() method terminates, the object must be discarded.
 * For further edits, create a new GraphProcessor object.
 */
class GraphProcessor(
    private val graph: Graph,
    private val conf: Configuration,
    private val weights: List<Pair<String, Int>>
) {

    private val random: Random = Random(conf.randomSeed)
    private val rootStats: GraphStats = graph.getStats(true)
    private val changeOperationWeights = weights

    // list: ( (min, max) -> operation )
    private val operationDistribution: MutableList<Pair<Pair<Int, Int>, String>> = LinkedList()

    private lateinit var stage: Stage

    private val globalDeltaSequence: DeltaSequence = DeltaSequence()
    private var globalGraph: Graph = graph
    private val impactType: ImpactType

    init {
        assert(conf.branchEditLength > 0)
        assert(conf.branchEditFocus in 0.0..1.0)
        assert(changeOperationWeights.map { e -> e.second }.reduce { d1, d2 -> d1 + d2 } == 100)

        //Assure convergence of the nondeterministic edit algorithm:
        assert(getWeightOf("ADD_SIMPLE") + getWeightOf("ADD_REGION") >= 1)

        impactType = if (conf.atomicCounting) {
            ImpactType.ATOMIC
        } else {
            ImpactType.SUM
        }

        var start = 0
        for (element in changeOperationWeights) {
            operationDistribution.add(Pair(Pair(start, start + element.second), element.first))
            start += element.second
        }

        clearStage()
    }

    private fun getWeightOf(operation: String): Int {
        return changeOperationWeights.find { values -> values.first == operation }?.second ?: 0
    }

    /**
     * Loop until the desired edit length is reached.
     * Iteration time of the implemented naive (try-check-rollback) algorithm is non-deterministic but termination is
     * guaranteed because addNode is always viable as a 1-impact operation.
     */
    fun exec(
        persistGraph: (Stage) -> Unit,
        persistDeltas: (Stage) -> Unit,
        produceVitruviusChange: Boolean = false,
        ecoreHandler: EcoreHandler? = null,
        eObjectInventor: EObjectInventor? = null): Stage {

        assert(!produceVitruviusChange || (ecoreHandler != null && eObjectInventor != null))

        var currentEditLength = 0
        var workingRegionName: String? = null

        conf.atomicCounting

        while (currentEditLength < conf.branchEditLength) {

            val p = random.nextInt(0, 100)
            var operationType: String? = null

            for (candidate in operationDistribution) {
                val interval = candidate.first
                if (interval.first <= p && p < interval.second) {
                    operationType = candidate.second
                    break
                }
            }

            val allRegions = stage.graph.getRegionsRecursive().toList()
            var workingRegion: Region? = allRegions.find { r -> r.name == workingRegionName }

            val changeRegionP = random.nextDouble(0.0, 1.0)
            if (allRegions.isNotEmpty() && (changeRegionP > conf.branchEditFocus || workingRegion == null)) {
                workingRegion = allRegions[random.nextInt(0, allRegions.size)]
            }
            workingRegionName = workingRegion?.name

            // Determine operation
            val operation = rollForOperation(operationType!!, workingRegion)
            // Determine impact
            val impact = computeImpact(operation)

            if (currentEditLength + impact <= conf.branchEditLength) {
                //apply stage
                if (operation != null) {
                    assert(
                        impactType != ImpactType.ATOMIC || impact == operation.flatten().size,
                        { "Miscount for ${operation}: expected $impact ops, got ${operation.flatten().size} ops" })
                    operation.apply()
                    globalDeltaSequence.deltaOperations.add(operation)

                    // Also ensure that EObjects exist for persisting VitruviusChanges
                    if (produceVitruviusChange) {
                        operation.toVitruviusEChanges(eObjectInventor!!, ecoreHandler!!)
                    }
                }

                currentEditLength += impact

                if (impact > 0 && conf.stepwiseExport){
                    persistGraph(Stage(globalGraph, globalDeltaSequence))
                    persistDeltas(Stage(globalGraph, globalDeltaSequence))
                }

            }
        }

        val finalStage = Stage(globalGraph, globalDeltaSequence)
        if (!conf.stepwiseExport){
            persistGraph(finalStage)
            persistDeltas(finalStage)
        }

        return finalStage
    }

    private fun rollForOperation(operation: String, workingRegion: Region?): DeltaOperation? {
        return when (operation) {
            "ADD_SIMPLE" -> addSimpleNode(workingRegion)
            "ADD_REGION" -> addRegion(workingRegion)
            "DELETE_NODE" -> deleteNode(workingRegion)
            "MOVE_NODE" -> moveNode(workingRegion)
            "CHANGE_LABEL" -> changeLabel(workingRegion)
            "ADD_EDGE" -> addEdge(workingRegion)
            "DELETE_EDGE" -> deleteEdge(workingRegion)
            else -> throw IllegalArgumentException("${operation} is unknown!")
        }
    }

    private fun computeImpact(operation: DeltaOperation?): Int {
        if (operation == null) {
            return 0
        }
        return when (impactType) {
            ImpactType.ATOMIC -> operation.getAtomicLength()
            ImpactType.SUM    -> 1
        }
    }

    private fun applyStageGlobal() {
        globalDeltaSequence.pushOperations(stage.deltaSequence.operations())
        globalGraph = stage.graph
    }

    private fun clearStage() {
        stage = Stage(globalGraph.deepCopy(), DeltaSequence())
    }

    private fun randomGlobalStageRegion(): Region? {
        val allRegions = stage.graph.getRegionsRecursive().toList()
        if(allRegions.isEmpty()) return null
        val p = random.nextInt(0, allRegions.size + 1)
        if ( p == allRegions.size) return null
        return allRegions[p]
    }

    /**
     * Adds a new [SimpleNode] to the specified (sub-)[Graph].
     * This operation does not add edges.
     * Therefore, the connectedness property may become violated.
     *
     * @param region the region to add the new [Node] to. If null, the root [Graph] is used.
     * @return [AddNode]
     */
    private fun addSimpleNode(region: Region?): AddNode {
        val targetGraph = region?.graph ?: stage.graph

        val op = AddNode(
            operationID = DeltaOperation.generateId(),
            nodeName = "SN_"+UUID.randomUUID().toString(),
            nodeID = Graph.generateId(),
            nodeType = NodeType.SIMPLE,
            toRegionID = region?.id ?: "root",
            null,
            containingGraph = targetGraph)
        stage.deltaSequence.pushOperation(op)
        return op
    }

    /**
     * Adds a new [Region] to the specified (sub-)[Graph].
     * This operation does not add edges. Therefore, the connectedness property may become violated.
     *
     * @param region the region to add the new [Node] to. If null, the root [Graph] is used.
     */
    private fun addRegion(region: Region?): AddNode {

        val targetGraph = region?.graph ?: stage.graph
        val op = AddNode(
            operationID = DeltaOperation.generateId(),
            nodeName = "RE_"+UUID.randomUUID().toString(),
            nodeID = Graph.generateId(),
            nodeType = NodeType.REGION,
            toRegionID = region?.id ?: "root",
            null,
            containingGraph = targetGraph)
        stage.deltaSequence.pushOperation(op)
        return op
    }

    /**
     * Deletes a single node from [region]
     * If the given [Region] contains no [Node], this operation returns without a result.
     */
    private fun deleteNode(region: Region?): DeleteNode? {
        val graph = region?.graph ?: stage.graph
        if (graph.nodes.isEmpty()) {
            return null
        }

        val node = graph.randomNode(random)
        val op = deleteNode(region, node)
        stage.deltaSequence.pushOperation(op)
        return op
    }

    /**
     * Delete a [Node] from the given [Region].
     * This method assumes that the given Region contains the given Node.
     * This method removes the Node from the stage [Graph]. It also removes all [Edge]s from the stage Graph that
     * contain the Node as start or end.
     * If the given Node is a Region itself, the operation is executed recursively on all Nodes its subgraph contains.
     * The [DeleteNode] delta operation is returned. All recursive deletes are executed in order and part of the
     * returned object.
     *
     * @param region
     * @param node
     */
    private fun deleteNode(region: Region?, node: Node): DeleteNode {
        val graph = region?.graph ?: stage.graph
        var label: Label? = null
        if(node is SimpleNode){
            label = node.label
        }
        return DeleteNode(
            id = DeltaOperation.generateId(),
            nodeName = node.name,
            nodeID = node.id,
            label = label,
            fromRegionID = region?.id ?: "root",
            nodeImplications = LinkedList(),
            edgeImplications = LinkedList(),
            node,
            graph,
            directDelete = true)
    }

    /**
     * Delete an [Edge] from the staged [Graph].
     * If the given [Region] contains no Edge, this operation returns without a result.
     */
    private fun deleteEdge(region: Region?): DeleteEdge? {
        val graph = region?.graph ?: stage.graph
        if (graph.edges.isEmpty()) {
            return null
        }
        val edge = graph.edges[random.nextInt(0, graph.edges.size)]
        val op = DeleteEdge(
            id = DeltaOperation.generateId(),
            nodeAID = edge.a.id,
            nodeBID = edge.b.id,
            fromRegionID = region?.id ?: "root",
            edgeID = edge.id,
            edgeToDelete = edge,
            containingGraph = graph)
        stage.deltaSequence.pushOperation(op)
        return op
    }

    /**
     * Move a [Node] from the given [Region] to another [Region].
     * If the given Region contains no Nodes, this operation returns without a result.
     * If the graph contains only one Region, this operation returns without a result.
     * This operation also moves all [Edge]s to assure that each Edge is located within the [Graph] of its start Node.
     *
     * REGIONS CAN NOT BE MOVED BECAUSE THIS WOULD VIOLATE THE COMPOSITION TREE STRUCTURE!
     */
    private fun moveNode(region: Region?): MoveNode? {
        val graph = region?.graph ?: stage.graph
        val simpleNodes = graph.nodes.filterIsInstance<SimpleNode>()
        if (simpleNodes.isEmpty()) {
            return null
        }
        val allRegions = stage.graph.getRegionsRecursive().toList()
        if (allRegions.size < 2) {
            return null
        }
        var targetRegion: Region? = null
        do {
            val p = random.nextInt(0, allRegions.size + 1)
            if(p < allRegions.size){
                targetRegion = allRegions[p]
            }
        } while (targetRegion == region)
        val node = simpleNodes[random.nextInt(0, simpleNodes.size)]
        val targetGraph = targetRegion?.graph ?: stage.graph
        val op = MoveNode(
            id = DeltaOperation.generateId(),
            nodeID = node.id,
            toRegionID = targetRegion?.id ?: "root",
            fromRegionID = region?.id ?: "root",
            edgeImplications = LinkedList(),
            node = node,
            fromGraph = graph,
            toGraph = targetGraph
        )
        stage.deltaSequence.pushOperation(op)
        return op
    }

    private fun moveEdgesWithNode(oldRegion: Region?, newRegion: Region?, node: Node): MutableList<MoveEdge>{
        val oldGraph = oldRegion?.graph ?: stage.graph
        val newGraph = newRegion?.graph ?: stage.graph
        val result: MutableList<MoveEdge> = LinkedList()
        val edgesToMove = oldGraph.edges.filter { e -> e.a == node }

        for(edge in edgesToMove){
            oldGraph.edges.remove(edge)
            newGraph.edges.add(edge)
            result.add(
                MoveEdge(
                    id = DeltaOperation.generateId(),
                    nodeAID = edge.a.id,
                    nodeBID = edge.b.id,
                    newRegionID = newRegion?.id ?: "root",
                    oldRegionID = oldRegion?.id ?: "root",
                    edgeID = edge.id,
                    edge = edge,
                    oldGraph = oldGraph,
                    newGraph = newGraph)
            )
        }
        return result
    }

    /**
     * If the given [Region] contains no [SimpleNode], this operation returns without a result.
     */
    private fun changeLabel(region: Region?): ChangeLabel? {
        val graph = region?.graph ?: stage.graph
        if (graph.nodes.isEmpty()) {
            return null
        }
        val simpleNodesInGraph = graph.nodes.filterIsInstance<SimpleNode>()
        if (simpleNodesInGraph.isEmpty()) {
            return null
        }
        val simpleNode = simpleNodesInGraph[random.nextInt(0, simpleNodesInGraph.size)]
        val oldLabel = simpleNode.label
        do {
            simpleNode.label = SimpleNode.randomLabel(random)
        }
        while (oldLabel == simpleNode.label)
        val op = ChangeLabel(
            id = DeltaOperation.generateId(),
            nodeID = simpleNode.id,
            newLabel = simpleNode.label,
            oldLabel = oldLabel,
            node = simpleNode)
        stage.deltaSequence.pushOperation(op)
        return op
    }

    /**
     * Add a new [Edge].
     * In certain random cases (e.g. if the given [Region] is empty, or we add duplicate edges), this operation
     * returns without a result.
     */
    private fun addEdge(region: Region?): AddEdge? {
        // Find node a
        val graph = region?.graph ?: stage.graph
        if (graph.nodes.isEmpty()) {
            return null
        }
        val nodeA: Node = graph.randomNode(random)

        // Find node b
        val p = random.nextDouble(0.0, 1.0)
        val isDistorted = p <= conf.edgeDistortion
        val graphB =
            if (!isDistorted)
                graph
            else
                randomGlobalStageRegion()?.graph ?: stage.graph

        if (graphB.nodes.isEmpty()) {
            return null
        }
        val nodeB = graphB.randomNode(random)

        // Assure we avoid duplicate edges
        if (graph.edges.any { e ->
            e.a.id == nodeA.id && e.b.id == nodeB.id
        }) {
            return null
        }

        val op = AddEdge(
            id = DeltaOperation.generateId(),
            nodeAID = nodeA.id,
            nodeBID = nodeB.id,
            toRegionID = region?.id ?: "root",
            edgeID = Graph.generateId(),
            newEdge = null,
            toGraph = graph,
            nodeBGraph = graphB)
        stage.deltaSequence.pushOperation(op)
        return op
    }

}