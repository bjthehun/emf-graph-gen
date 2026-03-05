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

import graphmodel.Graph
import graphmodel.Node
import graphmodel.Region
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import tools.vitruv.change.atomic.EChange
import java.util.*
import kotlin.collections.ArrayList

/**
 * Move a [Node] from one [Region] to another one.
 * If a Region is null, the graph root is used instead.
 * EdgeImplications are the movement of Edges to be located in the same Graph as their start Node.
 * <strong>This operation must assure that regions do not have cycles in their composition structure!</strong>
 */
class MoveNode(/*all*/ id: String,
               val nodeID: String,
               val targetRegionID: String = "root",
               val oldRegionID: String = "root",
               /*all*/
               val edgeImplications: MutableList<MoveEdge> = LinkedList(),
               /*one-way*/
                val node: Node?,
                val fromGraph: Graph?,
                val toGraph:  Graph?
    ) : DeltaOperation(id) {

    private val description = "MoveNode"

    override fun flatten(): List<DeltaOperation> {
        val result: MutableList<DeltaOperation> = LinkedList()
        for (op in edgeImplications){
            result.addAll(op.flatten())
        }
        result.add(this)
        return result
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {

        edgeImplications.forEach{ei -> ei.generate(classes, factory, filter, label, nodeType)}

        val operation = factory.create(classes[description])
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(idAttribute, id)

        val edgeImplicationRefs = operation.eClass().getEStructuralFeature("edgeImplications")
        (operation.eGet(edgeImplicationRefs) as java.util.List<Any>).addAll(edgeImplications.map { e -> e.buffer!! })

        val nodeIDAttribute = operation.eClass().getEStructuralFeature("nodeID")
        val targetRegionIDAttribute = operation.eClass().getEStructuralFeature("targetRegionID")
        val oldRegionIDAttribute = operation.eClass().getEStructuralFeature("oldRegionID")
        operation.eSet(nodeIDAttribute, nodeID)
        operation.eSet(targetRegionIDAttribute, targetRegionID)
        operation.eSet(oldRegionIDAttribute, oldRegionID)

        this.buffer = operation
        return operation
    }

    override fun toVitruviusEChanges(): List<EChange<Any>> {
        // EObject factory
        val ecoreMetamodelHandler = GRAPH_METAMODEL_HANDLER
        val eClasses = ecoreMetamodelHandler.getClassMap()
        val eFactory = ecoreMetamodelHandler.getModelFactory()

        // Create edge EObject
        val edgeEObject = node!!.generate(eClasses, eFactory, setOf(), null, null)
        // Create graph EObjects
        val fromGraphEObject = fromGraph!!.generate(eClasses, eFactory, setOf(), null, null)
        val toGraphEObject = toGraph!!.generate(eClasses, eFactory, setOf(), null, null)
        // Get EReference
        val graphEdgesEReference = fromGraphEObject.eClass()
            .getEStructuralFeature("edges") as EReference

        val atomicEChangeFactory = ATOMIC_CHANGE_FACTORY()
        val changes = ArrayList(edgeImplications.flatMap { op -> op.toVitruviusEChanges() })
        // 1. RemoveEReference from old graph
        changes.add(atomicEChangeFactory.createRemoveReferenceChange(
            fromGraphEObject,
            graphEdgesEReference,
            edgeEObject,
            0
        ))
        // 2. InsertEReference to old graph
        changes.add(atomicEChangeFactory.createInsertReferenceChange(
            toGraphEObject,
            graphEdgesEReference,
            edgeEObject,
            0
        ))
        return changes
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is MoveNode){
            for (moveEdge in edgeImplications) {
                if (!other.edgeImplications.any { it.deepEquals(moveEdge) }) return false
            }
            return nodeID == other.nodeID && targetRegionID == other.targetRegionID &&
                        oldRegionID == other.oldRegionID
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject): MoveNode {

            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id"), true) as String
            val edgeImplications = (eObject.eGet(eObject.eClass().
            getEStructuralFeature("edgeImplications"), true) as List<EObject>).map { e ->
                MoveEdge.parse(e) } as MutableList<MoveEdge>

            var nodeID: String? = null
            var targetRegionID: String? = null
            var oldRegionID: String? = null

            nodeID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeID"), true) as String
            targetRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("targetRegionID"), true) as String
            oldRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("oldRegionID"), true) as String


            return MoveNode(id, nodeID,  targetRegionID,  oldRegionID, edgeImplications,
                null, null, null)
        }
    }
}