/**
 * Copyright 2023 Karl Kegel
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
package graphmodel

import ecore.EObjectSource
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import util.GraphStats
import java.util.*

class Region(id: String, name: String, val graph: Graph) : Node(id, name), EObjectSource {

    private val description = "Region"

    fun getStats(recursive: Boolean): GraphStats {
        return graph.getStats(recursive)
    }

    override fun deepCopy(): Node {
        return Region(id, name, graph.deepCopy())
    }

    override fun generate(classes: Map<String, EClass>, factory: EFactory, filter: Set<String>,
                                   label: EEnum?, nodeType: EEnum?): EObject {
        if (super.buffer == null) {
            val region = factory.create(classes[description])
            super.buffer = region
        }

        if (filter.contains("Node")) {
            val region = buffer!!
            val nameAttribute = region.eClass().getEStructuralFeature("name")
            val graph = region.eClass().getEStructuralFeature("graph")
            val idAttribute = region.eClass().getEStructuralFeature("id")
            region.eSet(idAttribute, id)

            region.eSet(nameAttribute, super.name)
            region.eSet(graph, this.graph.generate(classes, factory, filter, label, nodeType))
        }else{
            this.graph.generate(classes, factory, filter, label, nodeType)
        }

        return super.buffer!!
    }

    override fun deepEquals(other: Any): Boolean {
        if(other is Region){
            return name == other.name && graph.deepEquals(other.graph)
        }
        return false
    }

    companion object {

        fun construct(predef: EObject): Region {
            val nameAttribute = predef.eClass().getEStructuralFeature("name")
            val name = predef.eGet(nameAttribute, true) as String
            val graphEObject = predef.eContents()[0]

            var regionID: String? = null
            val idAttribute = predef.eClass().getEStructuralFeature("id")
            regionID = predef.eGet(idAttribute, true) as String


            //Graph ID will be set inside the Graph constructor
            return Region(regionID, name, Graph(null, LinkedList(), LinkedList(), graphEObject, isRoot = false))
        }

    }

}