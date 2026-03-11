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
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.impl.EEnumLiteralImpl
import tools.vitruv.change.atomic.EChange

/**
 * Add a [Node] ([SimpleNode] or [Region]) to a specified Region.
 * If the specified region is null, it is added to the root [Graph] directly.
 */
class AddNode(/*all*/       operationID: String,
              /*no id*/     val nodeName: String,
              /*with id*/   val nodeID: String,
              /*all*/       private val nodeType: NodeType,
              /*with id*/   val toRegionID: String = "root",
              /*one-way*/  var node: Node?,
              /*one-way*/  val containingGraph: Graph?) : DeltaOperation(operationID) {

    private val description = "AddNode"

    override fun flatten(): List<DeltaOperation> {
        return listOf(this)
    }

    /**
     * Creates a new [Node] with [nodeID] and [nodeName].
     * If [nodeType] == Region, this node also is a [Region],
     * else it is a [SimpleNode] with [Label.GREY] as label.
     */
    override fun apply() {
        node =
            if (nodeType == NodeType.SIMPLE) {
                SimpleNode(
                    id = nodeID,
                    name = nodeName,
                    label = Label.GREY
                )
            } else {
                Region(
                    id = nodeID,
                    name = nodeName,
                    graph = Graph(
                        id = Graph.generateId()
                    )
                )
            }
        containingGraph!!.nodes.add(node!!)
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {
        val operation = factory.create(classes[description])

        val nodeNameAttribute = operation.eClass().getEStructuralFeature("nodeName")
        val nodeTypeAttribute = operation.eClass().getEStructuralFeature("nodeType")
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(nodeNameAttribute, nodeName)
        operation.eSet(nodeTypeAttribute, nodeType!!.getEEnumLiteral(this.nodeType.name))
        operation.eSet(idAttribute, super.id)

        val nodeIDAttribute = operation.eClass().getEStructuralFeature("nodeID")
        val toRegionIDAttribute = operation.eClass().getEStructuralFeature("toRegionID")
        operation.eSet(nodeIDAttribute, nodeID)
        operation.eSet(toRegionIDAttribute, toRegionID)

        this.buffer = operation
        return operation
    }

    override fun toVitruviusEChanges(eObjectInventor: EObjectInventor, ecoreHandler: EcoreHandler): List<EChange<Any>> {
        // Setup graph model factory
        val classes = ecoreHandler.getClassMap()
        val factory = ecoreHandler.getModelFactory()
        val greyEnum = ecoreHandler.getEnumMap()["Label"]!!

        // Create node EObject
        val nodeElement = eObjectInventor.getMappingForNode(node!!)
        val nodeEClass = nodeElement.eClass()
        // Create graph EObject
        val graphElement = containingGraph!!.generate(classes, factory, setOf(), null, null)
        // Insert node into graph
        val graphNodeEReference = graphElement.eClass().getEStructuralFeature("nodes") as EReference
        // Setup change model factory
        val changeFactory = ATOMIC_CHANGE_FACTORY()
        val changes = ArrayList<EChange<Any>>()

        // 1. Create node
        changes.add(changeFactory.createCreateEObjectChange(nodeElement) as EChange<Any>)
        // 2. Set id
        changes.add(changeFactory.createReplaceSingleAttributeChange(
            nodeElement,
            nodeEClass.getEStructuralFeature("id") as EAttribute,
            null,
            node!!.id
        ))
        if (node is SimpleNode) {
            // 3. Set label
            changes.add(changeFactory.createReplaceSingleAttributeChange(
                nodeElement,
                nodeEClass.getEStructuralFeature("label") as EAttribute,
                null,
                greyEnum.getEEnumLiteral(0).value
            ))
        }
        else if (node is Region) {
            val region = node as Region
            // Retrieve graph EObject of region
            val regionGraphEObject = region.graph.generate(classes, factory, setOf(), null, null)
            // 4. Create Region Graph
            changes.add(changeFactory.createCreateEObjectChange(
                regionGraphEObject
            ) as EChange<Any>)
            // 5. Add Reference of Region to Graph
            changes.add(changeFactory.createInsertReferenceChange(
                nodeElement,
                nodeEClass.getEStructuralFeature("graph") as EReference,
                regionGraphEObject,
                0
            ))
        }
        // 4./.6. Add to graph
        changes.add(changeFactory.createInsertReferenceChange(
            graphElement,
            graphNodeEReference,
            nodeElement,
            0
        ))
        return changes
    }

    override fun getAtomicLength(): Int {
        return 1
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is AddNode){
            return nodeName == other.nodeName && nodeType == other.nodeType &&
                        toRegionID == other.toRegionID && nodeID == other.nodeID
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject): AddNode {
            val nodeName = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeName"), true) as String
            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id"), true) as String
            val typeIndex = (eObject.eGet(eObject.eClass().getEStructuralFeature("nodeType"), true) as EEnumLiteralImpl).value
            val nodeType = NodeType.entries[typeIndex]
            val toRegionID = eObject.eGet(eObject.eClass().getEStructuralFeature("toRegionID"), true) as String
            val nodeID = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeID"), true) as String

            return AddNode(id, nodeName, nodeID, nodeType, toRegionID, null, null)
        }

    }
}