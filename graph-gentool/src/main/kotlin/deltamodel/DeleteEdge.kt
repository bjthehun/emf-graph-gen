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
                 /*no id*/  val nodeAName: String?,
                 /*with id*/val nodeAID: String?,
                 /*no id*/  val nodeBName: String?,
                 /*with id*/val nodeBID: String?,
                 /*no id*/  val fromRegionName: String? = "root",
                 /*with id*/val fromRegionID: String? = "root",
                 /*with id*/val edgeID: String?,
                 /*one-way*/val edgeToDelete: Edge?,
                 /*one-way*/val containingGraph: Graph?,
                 /*all*/    val serializeWithIDs: Boolean) : DeltaOperation(id) {

    private val description = "DeleteEdge"

    override fun flatten(): List<DeltaOperation> {
        return listOf(this)
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {
        val operation = factory.create(classes[description])
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(idAttribute, id)

        if(serializeWithIDs) {
            val nodeAIDAttribute = operation.eClass().getEStructuralFeature("nodeAID")
            val nodeBIDAttribute = operation.eClass().getEStructuralFeature("nodeBID")
            val fromRegionIDAttribute = operation.eClass().getEStructuralFeature("fromRegionID")
            val edgeIDAttribute = operation.eClass().getEStructuralFeature("edgeID")
            operation.eSet(nodeAIDAttribute, nodeAID)
            operation.eSet(nodeBIDAttribute, nodeBID)
            operation.eSet(fromRegionIDAttribute, fromRegionID)
            operation.eSet(edgeIDAttribute, edgeID)
        }else {
            val nodeAAttribute = operation.eClass().getEStructuralFeature("nodeA")
            val nodeBAttribute = operation.eClass().getEStructuralFeature("nodeB")
            val fromRegionAttribute = operation.eClass().getEStructuralFeature("fromRegion")
            operation.eSet(nodeAAttribute, nodeAName)
            operation.eSet(nodeBAttribute, nodeBName)
            operation.eSet(fromRegionAttribute, fromRegionName)
        }

        this.buffer = operation
        return operation
    }

    override fun toVitruviusEChanges(): List<EChange<Any>> {
        // Set up EObject factory
        val eObjectFactory = GRAPH_METAMODEL_HANDLER
        val classes = eObjectFactory.getClassMap()
        val factory = eObjectFactory.getModelFactory()
        // Retrieve Edge EObject
        val edgeEObject = edgeToDelete!!.generate(classes, factory, setOf(), null, null)
        val edgeEClass = edgeEObject.eClass()
        // Retrieve Node EObjects
        val nodeAEObject = edgeToDelete.a.generate(classes, factory, setOf(), null, null)
        val nodeBEObject = edgeToDelete.a.generate(classes, factory, setOf(), null, null)
        val edgeNodeRefs = edgeEClass.getEStructuralFeature("nodes") as EReference
        // Retrieve containing Graph
        val graphEObject = containingGraph!!.generate(classes, factory, setOf(), null, null)

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

    override fun deepEquals(other: Any): Boolean {
        if(other is DeleteEdge){
            return if(serializeWithIDs){
               nodeAID == other.nodeAID && nodeBID == other.nodeBID &&
                        fromRegionID == other.fromRegionID && edgeID == other.edgeID
            }else{
                val res = nodeAName == other.nodeAName && nodeBName == other.nodeBName &&
                        fromRegionName == other.fromRegionName
                if(idEquals(other) && !res){
                    throw AssertionError("Incoherent Comparison DeleteEdge: $this != $other")
                }
                res
            }
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject, serializeWithIDs: Boolean): DeleteEdge {
            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id")) as String

            var nodeAName: String? = null
            var nodeBName: String? = null
            var nodeAID: String? = null
            var nodeBID: String? = null
            var fromRegionName: String? = null
            var fromRegionID: String? = null
            var edgeID: String? = null

            if(serializeWithIDs) {
                nodeAID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeAID")) as String
                nodeBID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeBID")) as String
                fromRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("fromRegionID")) as String
                edgeID = eObject.eGet(eObject.eClass().getEStructuralFeature("edgeID")) as String
            }else {
                nodeAName = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeA")) as String
                nodeBName = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeB")) as String
                fromRegionName = eObject.eGet(eObject.eClass().getEStructuralFeature("fromRegion")) as String
            }

            return DeleteEdge(id, nodeAName, nodeAID, nodeBName, nodeBID, fromRegionName, fromRegionID, edgeID, null, null,serializeWithIDs)
        }

    }
}