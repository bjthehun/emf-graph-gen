/**
 * Copyright 2023 Karl Kegel, Benedikt Jutz
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

import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.ExtendedMetaData
import org.eclipse.emf.ecore.util.BasicExtendedMetaData
import org.eclipse.emf.ecore.xmi.XMLResource
import org.eclipse.emf.ecore.xmi.XMLResource.MissingPackageHandler
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.common.util.URI
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

open class EcoreMetamodelHandler(metamodel: URI) {
    protected var metamodelRoot: EPackage? = null
    protected var resourceSet: ResourceSet? = null

    init {
        Resource.Factory.Registry.INSTANCE.extensionToFactoryMap["ecore"] = EcoreResourceFactoryImpl()
        Resource.Factory.Registry.INSTANCE.extensionToFactoryMap["xmi"] = XMIResourceFactoryImpl()
        // Set up resource set
        resourceSet = ResourceSetImpl()
        val extendedMetaData: ExtendedMetaData = BasicExtendedMetaData(resourceSet!!.packageRegistry)
        resourceSet!!.loadOptions[XMLResource.OPTION_EXTENDED_META_DATA] = extendedMetaData

        // Setup metamodel from URI
        val metamodelResource = resourceSet!!.getResource(metamodel, true)
        val eObject = metamodelResource!!.contents[0]
        if (eObject is EPackage) {
            metamodelRoot = eObject
            resourceSet!!.packageRegistry[metamodelRoot!!.nsURI] = metamodelRoot!!
        } else {
            throw Exception("Unsupported ECORE specification.")
        }

        val mph = object : MissingPackageHandler {
            override fun getPackage(p0: String?): EPackage {
                return metamodelRoot as EPackage
            }
        }

        resourceSet!!.loadOptions[XMLResource.OPTION_MISSING_PACKAGE_HANDLER] = mph
    }

    fun getModelFactory(): EFactory {
        return metamodelRoot!!.eFactoryInstance
    }

    fun getClassMap(): Map<String, EClass> {
        val metaMap: MutableMap<String, EClass> = TreeMap()
        for (e in metamodelRoot!!.eClassifiers) {
            if (e is EClass) {
                metaMap[e.getName()] = e
            }
        }
        return metaMap
    }

    fun getEnumMap(): Map<String, EEnum> {
        val enumMap: MutableMap<String, EEnum> = TreeMap()
        for (e in metamodelRoot!!.eClassifiers) {
            if (e is EEnum) {
                enumMap[e.getName()] = e
            }
        }
        return enumMap
    }

}