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

import ecore.DeepComparable
import ecore.EObjectSource
import ecore.IDComparable
import ecore.EcoreHandler
import org.eclipse.emf.ecore.EObject
import util.IndexedComparable
import java.util.*
import tools.vitruv.change.atomic.EChange
import tools.vitruv.change.atomic.TypeInferringAtomicEChangeFactory

abstract class DeltaOperation(val id: String) : EObjectSource, DeepComparable, IDComparable, IndexedComparable() {

    /**
     * Set the EObject after it was generated for later use
     */
    var buffer: EObject? = null

    /**
     * Returns the atomic DeltaOperations required to fully execute this DeltaOperation.
     * Some operations, e.g. [MoveNode], have additional [deltamodel.DeltaOperation] that need
     * to be executed as well.
     * This function automatically computes the operations required for flattening.
     *
     * @return [List]
     */
    abstract fun flatten(): List<DeltaOperation>

    /**
     * Returns the number of atomic DeltaOperations required to fully execute this DeltaOperation.
     * The following invariant must hold: flatten().size == getAtomicLength()
     *
     * @return Int
     */
    abstract fun getAtomicLength(): Int

    /**
     * Applies the operation with its given parameters.
     */
    abstract fun apply()

    abstract fun toVitruviusEChanges(ecoreHandler: EcoreHandler): List<EChange<Any>>



    override fun idEquals(other: Any): Boolean {
        if(other is DeltaOperation){
            return id == other.id
        }
        return false
    }

    companion object {
        /**
         * Atomic Vitruvius Change factory.
         */
        fun ATOMIC_CHANGE_FACTORY() = TypeInferringAtomicEChangeFactory.getInstance()
    
        fun generateId(): String {
            return UUID.randomUUID().toString()
        }
    }
}