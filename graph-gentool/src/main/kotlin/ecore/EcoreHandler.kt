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
package ecore

import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.BasicExtendedMetaData
import org.eclipse.emf.ecore.util.ExtendedMetaData
import org.eclipse.emf.ecore.xmi.XMLResource
import org.eclipse.emf.ecore.xmi.XMLResource.MissingPackageHandler
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl
import java.util.*

/**
 * An [ecore.EcoreHandler] handles multiple models with a shared metamodel [EPackage] under their given [URI].
 */
class EcoreHandler(metamodel: URI, registryExtension: String): EcoreMetamodelHandler(metamodel) {


    init {
        Resource.Factory.Registry.INSTANCE.extensionToFactoryMap[registryExtension] = XMIResourceFactoryImpl()
    }

    fun getRegisteredModels(): List<URI?> {
        return resourceSet!!.resources.map { it.uri }
    }

    fun getResource(modelUri: URI): Resource {
        return resourceSet!!.getResource(modelUri, false)!!
    }

    fun registerNewModel(modelUri: URI) {
        resourceSet!!.getResource(modelUri, true)
    }

    fun getModelRoot(modelUri: URI): EObject {
        return resourceSet!!.getResource(modelUri, false).contents[0]
    }

    fun saveModel(modelUri: URI) {
        resourceSet!!.getResource(modelUri, false).save(null)
    }

}