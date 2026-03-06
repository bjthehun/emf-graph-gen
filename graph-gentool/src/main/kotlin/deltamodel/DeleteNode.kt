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

package deltamodel

import ecore.EcoreHandler
import graphmodel.Graph
import graphmodel.Label
import graphmodel.Node
import graphmodel.Region
import graphmodel.SimpleNode
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.impl.EEnumLiteralImpl
import tools.vitruv.change.atomic.EChange
import java.util.*

/**
 * Delete a [Node] from the specified [Region].
 * If no Region is specified, it is assumed to be in the Graph root.
 * NodeImplications contains the [DeleteNode] actions that must be executed beforehand to "clean" the node.
 * EdgeImplications contains the [DeleteEdge] actions that must be executed beforehand to "clean" the node.
 */
class DeleteNode(/*all*/        id: String,
                 /*all*/        val nodeName: String,
                 /*no id*/      val nodeID: String?,
                 /*all*/        val label: Label?,
                 /*with id*/    val fromRegionID: String? = "root",
                 /*all*/        val nodeImplications: MutableList<DeleteNode>,
                 /*all*/        val edgeImplications: MutableList<DeleteEdge>,
                                val node: Node?,
                                val containingGraph: Graph?) : MultiDeltaOperation(id) {

    val description = "DeleteNode"

    override fun flatten(): List<DeltaOperation> {
        if (!generated) {
            generated = true
            // Edge Implications: Find Edges with node as source or target
            val edgesToDeleteDirectly = containingGraph!!.edges
                .filter{e -> e.a.id == nodeID || e.b.id == nodeID}
            edgesToDeleteDirectly.forEach { e ->
                edgeImplications.add(
                    DeleteEdge(
                        id = generateId(),
                        nodeAID = e.a.id,
                        nodeBID = e.b.id,
                        fromRegionID = this.fromRegionID,
                        edgeID = e.id,
                        edgeToDelete = e,
                        containingGraph = this.containingGraph
                    )
                )
            }

            // Node Implications: Delete all nodes, too
            if (node is Region) {
                val nodesToDeleteAlso = node.graph.nodes
                nodesToDeleteAlso.forEach { n ->
                    nodeImplications.add(
                        DeleteNode(
                            id = generateId(),
                            nodeName = n.name,
                            nodeID = n.id,
                            label = if (n is SimpleNode) n.label else null,
                            fromRegionID = node.id,
                            nodeImplications = ArrayList(),
                            edgeImplications = ArrayList(),
                            node = n,
                            containingGraph = node.graph
                        )
                    )
                }
            }
        }
        val result = ArrayList<DeltaOperation>(edgeImplications)
        for (nodeImplication in nodeImplications) {
            result.addAll(nodeImplication.flatten())
        }
        result.add(this)
        return result
    }

    override fun toVitruviusEChanges(ecoreHandler: EcoreHandler): List<EChange<Any>> {
        // Apply DeleteEdges, DeleteNodes for implications first
        val edgeImplicationEChanges = edgeImplications.flatMap { op -> op.toVitruviusEChanges(ecoreHandler) }
        val nodeImplicationEChanges = nodeImplications.flatMap { op -> op.toVitruviusEChanges(ecoreHandler) }

        // EObject Factory

        val eClasses = ecoreHandler.getClassMap()
        val eFactory = ecoreHandler.getModelFactory()
        val nodeEObject = node!!.generate(eClasses, eFactory, setOf("Node"), null, null)
        val nodeEClass = nodeEObject.eClass()

        // EChange creation
        val eChangeCreator = ATOMIC_CHANGE_FACTORY()

        val changes = ArrayList<EChange<Any>>()
        // Apply Edge Implications, Node Implications first
        changes.addAll(edgeImplicationEChanges)
        changes.addAll(nodeImplicationEChanges)
        // 0. Unset Label, if it is set
        if (label != null) {
            val labelEnumValue =
                ecoreHandler.getEnumMap()["Label"]!!.getEEnumLiteral(label.toString())
            changes.add(eChangeCreator.createReplaceSingleAttributeChange(
                nodeEObject,
                nodeEClass.getEStructuralFeature("label") as EAttribute,
                labelEnumValue,
                null
            ))
        }
        // 1. Unset ID
        changes.add(eChangeCreator.createReplaceSingleAttributeChange(
            nodeEObject,
            nodeEClass.getEStructuralFeature("id") as EAttribute,
            nodeID!!,
            null
        ))
        // 2. RemoveEReference of Graph
        val graphEObject = containingGraph!!.generate(eClasses, eFactory, setOf(), null, null)
        changes.add(eChangeCreator.createRemoveReferenceChange(
            graphEObject,
            nodeEObject.eContainmentFeature(),
            graphEObject,
            0
        ))
        // 3. DeleteEObject
        changes.add(eChangeCreator.createDeleteEObjectChange(
            nodeEObject
        ) as EChange<Any>)
        return changes
    }

    /**
     * Counts the number of atomic [deltamodel.DeleteEdge] and [DeleteNode] operations required to delete [node].
     * One operation is required to delete [node] itself, and one operation each to delete each edge to it.
     *
     * In addition, when [node] is a [Region], we recursively need to delete each [Graph], i.e. each [Node] and
     * each [graphmodel.Edge] in it.
     */
    override fun getAtomicLength(): Int {
        var nodeCosts = 1 + containingGraph!!
            .edges.count { e -> e.a.idEquals(node!!) || e.b.idEquals(node) }
        if (node is Region) {
            nodeCosts += countAllGraphElements(node.graph)
        }
        return nodeCosts
    }

    private fun countAllGraphElements(graph: Graph): Int {
        return graph.simpleSize() + graph.allRegions()
                .sumOf { region -> countAllGraphElements(region.graph) }
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {

        nodeImplications.forEach{ni -> ni.generate(classes, factory, filter, label, nodeType)}
        edgeImplications.forEach{ei -> ei.generate(classes, factory, filter, label, nodeType)}

        val operation = factory.create(classes[description])

        val nodeNameAttribute = operation.eClass().getEStructuralFeature("nodeName")
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(nodeNameAttribute, nodeName)
        operation.eSet(idAttribute, id)

        //Because it can be a Region without a label
        if(this.label !== null){
            val labelAttribute = operation.eClass().getEStructuralFeature("label")
            operation.eSet(labelAttribute, label!!.getEEnumLiteral(this.label.name))
        }

        val nodeIDAttribute = operation.eClass().getEStructuralFeature("nodeID")
        val fromRegionIDAttribute = operation.eClass().getEStructuralFeature("fromRegionID")
        operation.eSet(nodeIDAttribute, nodeID)
        operation.eSet(fromRegionIDAttribute, fromRegionID)

        val nodeImplicationRefs = operation.eClass().getEStructuralFeature("nodeImplications")
        (operation.eGet(nodeImplicationRefs) as java.util.List<Any>).addAll(nodeImplications.map { e -> e.buffer!! })

        val edgeImplicationRefs = operation.eClass().getEStructuralFeature("edgeImplications")
        (operation.eGet(edgeImplicationRefs) as java.util.List<Any>).addAll(edgeImplications.map { e -> e.buffer!! })

        this.buffer = operation
        return operation
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is DeleteNode){
            for (deleteNode in nodeImplications) {
                if (!other.nodeImplications.any { it.deepEquals(deleteNode) }) return false
            }
            for (deleteEdge in edgeImplications) {
                if (!other.edgeImplications.any { it.deepEquals(deleteEdge) }) return false
            }
            return this.nodeName == other.nodeName && this.nodeID == other.nodeID &&
                        this.fromRegionID == other.fromRegionID && this.label == other.label
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject): DeleteNode {

            val nodeName = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeName"), true) as String
            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id"), true) as String

            val labelAttribute = eObject.eClass().getEStructuralFeature("label")
            val labelProperty = eObject.eGet(labelAttribute, true)
            var label: Label? = null
            if(labelProperty != null) {
                val labelIndex = (labelProperty as EEnumLiteralImpl).value
                label = Label.entries[labelIndex]
            }

            val nodeImplications = (eObject.eGet(eObject.eClass().getEStructuralFeature("nodeImplications")) as List<EObject>)
                .map { e -> parse(e) }.toMutableList()
            val edgeImplications = (eObject.eGet(eObject.eClass().getEStructuralFeature("edgeImplications")) as List<EObject>)
                .map { e -> DeleteEdge.parse(e) }.toMutableList()

            val fromRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("fromRegionID"), true) as String
            val nodeID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeID"), true) as String

            return DeleteNode(id, nodeName, nodeID, label, fromRegionID,  nodeImplications, edgeImplications,
                null, null)
        }

    }
}