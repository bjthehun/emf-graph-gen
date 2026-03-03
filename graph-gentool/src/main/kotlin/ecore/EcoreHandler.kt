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

class EcoreHandler(metamodel: URI, model: URI, registryExtension: String): EcoreMetamodelHandler(metamodel) {

    private var modelResource: Resource? = null

    init {
        Resource.Factory.Registry.INSTANCE.extensionToFactoryMap[registryExtension] = XMIResourceFactoryImpl()
        modelResource = resourceSet!!.getResource(model, true)
    }

    fun getModelRoot(): EObject {
        return modelResource!!.contents[0]
    }

    fun saveModel() {
        modelResource!!.save(null)
    }

}