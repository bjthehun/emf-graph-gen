package ecore

import graphmodel.Edge
import graphmodel.Node
import graphmodel.SimpleNode
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EFactory
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference

/**
 * Construct artificial [EObject]s in order to serialize [EChange]s.
 */
class EObjectInventor(
    graphHandler: EcoreHandler
) {
    private val nodesReference: EReference
    private val edgesReference: EReference

    /**
     * EObject representing the root of the graph.
     */
    val rootObject: EObject

    /**
     * Model factory
     */
    private val factory: EFactory = graphHandler.getModelFactory()
    /**
     * EClass mapping
     */
    private val eClassMapping: Map<String, EClass> = graphHandler.getClassMap()

    /**
     * Mapping of ids to EObjects
     */
    private val mapping: MutableMap<String, EObject>

    init {
        rootObject = factory.create(eClassMapping["Graph"])
        mapping = HashMap()
        nodesReference = rootObject.eClass().getEStructuralFeature("nodes") as EReference
        edgesReference = rootObject.eClass().getEStructuralFeature("edges") as EReference
    }

    fun getMappingForEdge(edge: Edge): EObject {
        if (mapping.containsKey(edge.id)) {
            return mapping[edge.id]!!
        }

        val edgeEObject = factory.create(eClassMapping["Edge"])
        val edgeIdAttribute = edgeEObject.eClass().getEStructuralFeature("id") as EAttribute
        edgeEObject.eSet(edgeIdAttribute, edge.id)
        (rootObject.eGet(edgesReference) as java.util.List<EObject>).add(edgeEObject)
        mapping[edge.id] = edgeEObject
        return edgeEObject
    }

    fun getMappingForNode(node: Node): EObject {
        if (mapping.containsKey(node.id)) {
            return mapping[node.id]!!
        }

        val eClassName = if (node is SimpleNode) "SimpleNode" else "Region"
        val nodeEObject = factory.create(eClassMapping[eClassName])
        val edgeIdAttribute = nodeEObject.eClass().getEStructuralFeature("id") as EAttribute
        nodeEObject.eSet(edgeIdAttribute, node.id)
        (rootObject.eGet(nodesReference) as java.util.List<EObject>).add(nodeEObject)
        mapping[node.id] = nodeEObject
        return nodeEObject
    }
}