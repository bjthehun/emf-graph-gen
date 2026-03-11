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

import ecore.EObjectInventor
import ecore.EcoreHandler
import graphmodel.Edge
import graphmodel.Graph
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EReference
import tools.vitruv.change.atomic.EChange
import tools.vitruv.change.atomic.TypeInferringAtomicEChangeFactory


/**
 * Add a new [Edge].
 * The Edge is added to the (sub-)graph where the first Node is located.
 */
class AddEdge(/*all*/       id: String,
              /*with id*/   val nodeAID: String,
              /*with id*/   val nodeBID: String,
              /*with id*/   val toRegionID: String? = "root",
              /*with id*/   val edgeID: String,
              /*one-way */  var newEdge: Edge?,
              /*one-way */  val toGraph: Graph?,
              /*generate */ val nodeBGraph: Graph?) : DeltaOperation(id) {

    private val description = "AddEdge"

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
        val toRegionIDAttribute = operation.eClass().getEStructuralFeature("toRegionID")
        val edgeIDAttribute = operation.eClass().getEStructuralFeature("edgeID")
        operation.eSet(nodeAIDAttribute, nodeAID)
        operation.eSet(nodeBIDAttribute, nodeBID)
        operation.eSet(toRegionIDAttribute, toRegionID)
        operation.eSet(edgeIDAttribute, edgeID)

        this.buffer = operation
        return operation
    }

    /**
     * Creates a new edge and adds it to the edge set of [toGraph].
     */
    override fun apply() {
        // Create new edge
        newEdge = Edge(
            edgeID,
            a = toGraph!!.findNodeById(nodeAID)!!,
            b = nodeBGraph!!.findNodeById(nodeBID)!!
        )
        toGraph.edges.add(newEdge!!)
    }
   
    override fun toVitruviusEChanges(eObjectInventor: EObjectInventor, ecoreHandler: EcoreHandler): List<EChange<Any>> {
        val changes = ArrayList<EChange<Any>>()
        // Set up EcoreMetamodelHandler for model element generation

        val classes  = ecoreHandler.getClassMap()
        val factory = ecoreHandler.getModelFactory()
        // Set up EChangeFactory
        val changeFactory = TypeInferringAtomicEChangeFactory.getInstance()
        // Assume that nodes exist
        val nodeA = eObjectInventor.getMappingForNode(newEdge!!.a)
        val nodeB = eObjectInventor.getMappingForNode(newEdge!!.b)
        // Identify containing graph
        val graphElement = toGraph!!.generate(classes, factory, setOf("Node, Edge"), null, null)
        // Get Edge EObject
        val edgeEObject = eObjectInventor.getMappingForEdge(newEdge!!)

        // Change 1: CreateEdge
        changes.add(changeFactory.createCreateEObjectChange(edgeEObject) as EChange<Any>)
        // Change 2: SetEAttribute
        val idAttribute = edgeEObject.eClass().getEStructuralFeature("id") as EAttribute
        changes.add(changeFactory.createReplaceSingleAttributeChange(
            edgeEObject,
            idAttribute,
            null,
            id)
        )


        // Changes 3 and 4: InsertEReference
        val nodesReference = edgeEObject.eClass().getEStructuralFeature("nodes") as EReference
        changes.add(changeFactory.createInsertReferenceChange(edgeEObject, nodesReference, nodeA, 0))
        changes.add(changeFactory.createInsertReferenceChange(edgeEObject, nodesReference, nodeB, 0))
        // Ensure edge EObject is in graph EObject
        val graphEdgeEReference = graphElement.eClass().getEStructuralFeature("edges") as EReference

        // Change 5: InsertEReference
        changes.add(
            changeFactory.createInsertReferenceChange(
                graphElement,
                graphEdgeEReference,
                edgeEObject,
                0))

        return changes
    }

    override fun getAtomicLength(): Int {
        return 1
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is AddEdge){
            val res = this.nodeAID == other.nodeAID && this.nodeBID == other.nodeBID &&
                    this.toRegionID == other.toRegionID
            if(idEquals(other) && !res){
                throw AssertionError("Incoherent Comparison AddEdge: $this != $other")
            }
            return res
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject, ): AddEdge {
            val idAttribute = eObject.eClass().getEStructuralFeature("id")
            val id = eObject.eGet(idAttribute, true) as String

            val nodeAID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeAID")) as String
            val nodeBID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeBID")) as String
            val toRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("toRegionID")) as String
            val edgeID = eObject.eGet(eObject.eClass().getEStructuralFeature("edgeID")) as String
            
            return AddEdge(id,  nodeAID,  nodeBID,  toRegionID, edgeID,   null, null, nodeBGraph = null)
        }
    }

}