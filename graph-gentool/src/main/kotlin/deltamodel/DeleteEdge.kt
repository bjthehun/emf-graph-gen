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
import graphmodel.Edge
import graphmodel.Graph
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import tools.vitruv.change.atomic.EChange
import tools.vitruv.change.atomic.TypeInferringAtomicEChangeFactory

/**
 * Delete an [Edge].
 * In case the Edge occurs multiple times, only one occurrence is deleted.
 */
class DeleteEdge(/*all*/    id: String,
                 /*with id*/val nodeAID: String?,
                 /*with id*/val nodeBID: String?,
                 /*with id*/val fromRegionID: String? = "root",
                 /*with id*/val edgeID: String?,
                 /*one-way*/val edgeToDelete: Edge?,
                 /*one-way*/val containingGraph: Graph?) : DeltaOperation(id) {

    private val description = "DeleteEdge"

    override fun flatten(): List<DeltaOperation> {
        return listOf(this)
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {
        val operation = factory.create(classes[description])
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(idAttribute, id)

        val nodeAIDAttribute = operation.eClass().getEStructuralFeature("nodeAID")
        val nodeBIDAttribute = operation.eClass().getEStructuralFeature("nodeBID")
        val fromRegionIDAttribute = operation.eClass().getEStructuralFeature("fromRegionID")
        val edgeIDAttribute = operation.eClass().getEStructuralFeature("edgeID")
        operation.eSet(nodeAIDAttribute, nodeAID)
        operation.eSet(nodeBIDAttribute, nodeBID)
        operation.eSet(fromRegionIDAttribute, fromRegionID)
        operation.eSet(edgeIDAttribute, edgeID)

        this.buffer = operation
        return operation
    }

    override fun toVitruviusEChanges(ecoreHandler: EcoreHandler): List<EChange<Any>> {
        // Set up EObject factory
        val classes = ecoreHandler.getClassMap()
        val factory = ecoreHandler.getModelFactory()
        // Retrieve containing Graph
        val graphEObject = containingGraph!!.generate(classes, factory, setOf(), null, null)
        // Retrieve Node EObjects
        val nodeAEObject = edgeToDelete!!.a.generate(classes, factory, setOf("Node"), null, null)
        val nodeBEObject = edgeToDelete.b.generate(classes, factory, setOf("Node"), null, null)
        // Retrieve Edge EObject
        val edgeEObject = edgeToDelete.generate(classes, factory, setOf(), null, null)
        val edgeEClass = edgeEObject.eClass()

        val edgeNodeRefs = edgeEClass.getEStructuralFeature("nodes") as EReference


        val eChangeFactory = TypeInferringAtomicEChangeFactory.getInstance()
        val changes = ArrayList<EChange<Any>>()
        // 1. UnsetEAttribute
        changes.add(eChangeFactory.createReplaceSingleAttributeChange(
            edgeEObject,
            edgeEClass.getEStructuralFeature("id") as EAttribute,
            edgeID,
            null
        ))
        // 2. RemoveEReference to node A
        changes.add(eChangeFactory.createRemoveReferenceChange(
            edgeEObject,
            edgeNodeRefs,
            nodeAEObject,
            0
        ))
        // 3. RemoveEReference to node b
        changes.add(eChangeFactory.createRemoveReferenceChange(
            edgeEObject,
            edgeNodeRefs,
            nodeBEObject,
            0
        ))
        // 4. RemoveEReference of graph to edge
        changes.add(eChangeFactory.createRemoveReferenceChange(
            graphEObject,
            edgeEObject.eContainmentFeature(),
            edgeEObject,
            0
        ))
        // 5. DeleteEObject
        changes.add(eChangeFactory.createDeleteEObjectChange(
            edgeEObject
        ) as EChange<Any>)
        return changes
    }

    override fun getAtomicLength(): Int {
        return 1
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is DeleteEdge){
            return nodeAID == other.nodeAID && nodeBID == other.nodeBID &&
                        fromRegionID == other.fromRegionID && edgeID == other.edgeID
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject): DeleteEdge {
            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id")) as String

            val nodeAID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeAID")) as String
            val nodeBID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeBID")) as String
            val fromRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("fromRegionID")) as String
            val edgeID = eObject.eGet(eObject.eClass().getEStructuralFeature("edgeID")) as String

            return DeleteEdge(id,  nodeAID, nodeBID,  fromRegionID, edgeID, null, null)
        }

    }
}