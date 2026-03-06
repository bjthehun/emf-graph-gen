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
import graphmodel.Label
import graphmodel.Node
import graphmodel.SimpleNode
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.impl.EEnumLiteralImpl
import tools.vitruv.change.atomic.EChange

/**
 * Change the [Label] value of an existing [Node].
 * The uniqueness of the Label value must not be violated.
 */
class ChangeLabel(/*all*/       id: String,
                  /*with id*/   val nodeID: String,
                  /*all*/       val newLabel: Label,
                  /*all*/       val oldLabel: Label,
                  /*one-way*/   val node: SimpleNode?) : DeltaOperation(id) {

    private val description = "ChangeLabel"

    override fun flatten(): List<DeltaOperation> {
        return listOf(this)
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                          label: EEnum?, nodeType: EEnum?): EObject {
        val operation = factory.create(classes[description])

        val newLabelAttribute = operation.eClass().getEStructuralFeature("newLabel")
        val oldLabelAttribute = operation.eClass().getEStructuralFeature("oldLabel")
        val idAttribute = operation.eClass().getEStructuralFeature("id")
        operation.eSet(idAttribute, id)
        operation.eSet(newLabelAttribute, label!!.getEEnumLiteral(this.newLabel.name))
        operation.eSet(oldLabelAttribute, label.getEEnumLiteral(this.oldLabel.name))

        val nodeIDAttribute = operation.eClass().getEStructuralFeature("nodeID")
        operation.eSet(nodeIDAttribute, nodeID)

        this.buffer = operation
        return operation
    }

    override fun toVitruviusEChanges(ecoreHandler: EcoreHandler): List<EChange<Any>> {
        // Get SimpleNode
        val nodeElement = node!!.generate(
            ecoreHandler.getClassMap(),
            ecoreHandler.getModelFactory(),
            nodeType = null,
            filter = setOf(),
            label = null
        )
        // Translate old and new enums
        val labelENumInEcore = ecoreHandler.getEnumMap()["Label"]!!
        val oldLabelInEcore  = labelENumInEcore.getEEnumLiteral(oldLabel.toString())
        val newLabelInEcore  = labelENumInEcore.getEEnumLiteral(newLabel.toString())

        val eChangeFactory = ATOMIC_CHANGE_FACTORY()
        val changes = ArrayList<EChange<Any>>()
        // SetEAttribute
        changes.add(
            eChangeFactory.createReplaceSingleAttributeChange(
                nodeElement,
                nodeElement.eClass().getEStructuralFeature("label") as EAttribute,
                oldLabelInEcore,
                newLabelInEcore
            )
        )
        return changes
    }

    override fun getAtomicLength(): Int {
        return 1
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is ChangeLabel){
            return this.nodeID == other.nodeID
                    && this.newLabel == other.newLabel
                    && this.oldLabel == other.oldLabel
        }
        return false
    }

    companion object {

        fun parse(eObject: EObject, ): ChangeLabel {

            val newLabel = Label.entries[(eObject.eGet(eObject.eClass().getEStructuralFeature("newLabel")) as EEnumLiteralImpl).value]
            val oldLabel = Label.entries[(eObject.eGet(eObject.eClass().getEStructuralFeature("oldLabel")) as EEnumLiteralImpl).value]
            val id = eObject.eGet(eObject.eClass().getEStructuralFeature("id")) as String
            val nodeID: String = eObject.eGet(eObject.eClass().getEStructuralFeature("nodeID")) as String

            return ChangeLabel(id, nodeID, newLabel, oldLabel, null)
        }

    }

}