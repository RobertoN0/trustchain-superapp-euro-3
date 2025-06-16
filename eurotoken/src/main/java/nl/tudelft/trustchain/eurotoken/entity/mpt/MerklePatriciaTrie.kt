package nl.tudelft.trustchain.eurotoken.entity.mpt

import java.util.concurrent.ConcurrentHashMap

class MerklePatriciaTrie<T : MPTSerializable> {
    private var root: MPTNode? = null
    private val cache = ConcurrentHashMap<String, T>()

    /**
     * Insert element in the trie
     */
    fun put(item: T) {
        val key = item.getMPTKey()
        cache[key] = item
        root = putRecursive(root, key, item)
    }

    /**
     * Get element from the trie
     */
    fun get(key: String): T? {
        return cache[key] ?: getRecursive(root, key)
    }

    /**
     * Verifies if a key exists inside the trie
     */
    fun contains(key: String): Boolean {
        return get(key) != null
    }

    /**
     * Remove an element from the trie
     */
    fun remove(key: String): T? {
        val oldValue = cache.remove(key)
        root = removeRecursive(root, key)
        return oldValue
    }

    /**
     * Returns the root's hash
     */
    fun getRootHash(): ByteArray {
        return root?.getHash() ?: ByteArray(32)
    }

    /**
     * Generates an inclusion proof for a key
     */
    fun generateInclusionProof(key: String): InclusionProof {
        val proof = mutableListOf<ByteArray>()
        val value = get(key)
        val isIncluded = value != null

        collectProofNodes(root, key, proof)

        return InclusionProof(
            key = key,
            value = value?.toMPTBytes(),
            proof = proof,
            isIncluded = isIncluded
        )
    }

    /**
     * Return all the keys in the trie
     */
    fun getAllKeys(): List<String> {
        val keys = mutableListOf<String>()
        collectKeys(root, "", keys)
        return keys
    }

    /**
     * Returns the dimension of the trie
     */
    fun size(): Int = cache.size

    /**
     * Verifies if the trie is empty
     */
    fun isEmpty(): Boolean = root == null


    fun clear() {
        root = null
        cache.clear()
    }


    @Suppress("UNCHECKED_CAST")
    private fun putRecursive(node: MPTNode?, key: String, value: T): MPTNode {
        if (node == null) {
            return LeafNode(key, value)
        }

        return when (node) {
            is LeafNode<*> -> {
                val leafNode = node as LeafNode<T>
                val commonPrefix = getCommonPrefix(key, leafNode.keyEnd)

                when {
                    commonPrefix.length == key.length && commonPrefix.length == leafNode.keyEnd.length -> {
                        // Same key, substitute the value
                        LeafNode(leafNode.keyEnd, value)
                    }
                    commonPrefix.length == key.length -> {
                        // The new key is a prefix of the existing key
                        val branch = BranchNode<T>(value = value)
                        val remainingKey = leafNode.keyEnd.substring(commonPrefix.length)
                        val firstChar = hexCharToIndex(remainingKey[0])
                        branch.children[firstChar] = LeafNode(remainingKey.substring(1), leafNode.value)

                        if (commonPrefix.isNotEmpty()) {
                            ExtensionNode(commonPrefix, branch)
                        } else {
                            branch
                        }
                    }
                    commonPrefix.length == leafNode.keyEnd.length -> {
                        // The existing key is a prefix of the new key
                        val branch = BranchNode<T>(value = leafNode.value)
                        val remainingKey = key.substring(commonPrefix.length)
                        val firstChar = hexCharToIndex(remainingKey[0])
                        branch.children[firstChar] = LeafNode(remainingKey.substring(1), value)

                        if (commonPrefix.isNotEmpty()) {
                            ExtensionNode(commonPrefix, branch)
                        } else {
                            branch
                        }
                    }
                    else -> {
                        // Divergent keys
                        val branch = BranchNode<T>()

                        val remainingOldKey = leafNode.keyEnd.substring(commonPrefix.length)
                        val remainingNewKey = key.substring(commonPrefix.length)

                        val oldFirstChar = hexCharToIndex(remainingOldKey[0])
                        val newFirstChar = hexCharToIndex(remainingNewKey[0])

                        branch.children[oldFirstChar] = LeafNode(remainingOldKey.substring(1), leafNode.value)
                        branch.children[newFirstChar] = LeafNode(remainingNewKey.substring(1), value)

                        if (commonPrefix.isNotEmpty()) {
                            ExtensionNode(commonPrefix, branch)
                        } else {
                            branch
                        }
                    }
                }
            }

            is ExtensionNode -> {
                val commonPrefix = getCommonPrefix(key, node.sharedKey)

                when {
                    commonPrefix.length == node.sharedKey.length -> {
                        // The key shares the entire extension prefix
                        val remainingKey = key.substring(commonPrefix.length)
                        ExtensionNode(node.sharedKey, putRecursive(node.nextNode, remainingKey, value))
                    }
                    else -> {
                        // We have to split the extension
                        val branch = BranchNode<T>()
                        val remainingExtensionKey = node.sharedKey.substring(commonPrefix.length)
                        val remainingNewKey = key.substring(commonPrefix.length)

                        val extensionFirstChar = hexCharToIndex(remainingExtensionKey[0])
                        val newFirstChar = hexCharToIndex(remainingNewKey[0])

                        if (remainingExtensionKey.length == 1) {
                            branch.children[extensionFirstChar] = node.nextNode
                        } else {
                            branch.children[extensionFirstChar] = ExtensionNode(
                                remainingExtensionKey.substring(1),
                                node.nextNode
                            )
                        }

                        branch.children[newFirstChar] = LeafNode(remainingNewKey.substring(1), value)

                        if (commonPrefix.isNotEmpty()) {
                            ExtensionNode(commonPrefix, branch)
                        } else {
                            branch
                        }
                    }
                }
            }

            is BranchNode<*> -> {
                val branchNode = node as BranchNode<T>
                if (key.isEmpty()) {
                    // root in the branch
                    BranchNode(branchNode.children.clone(), value)
                } else {
                    val firstChar = hexCharToIndex(key[0])
                    val remainingKey = key.substring(1)
                    val newChildren = branchNode.children.clone()
                    newChildren[firstChar] = putRecursive(branchNode.children[firstChar], remainingKey, value)
                    BranchNode(newChildren, branchNode.value)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRecursive(node: MPTNode?, key: String): T? {
        if (node == null) return null

        return when (node) {
            is LeafNode<*> -> {
                if ((node as LeafNode<T>).keyEnd == key) node.value else null
            }

            is ExtensionNode -> {
                if (key.startsWith(node.sharedKey)) {
                    getRecursive(node.nextNode, key.substring(node.sharedKey.length))
                } else {
                    null
                }
            }

            is BranchNode<*> -> {
                if (key.isEmpty()) {
                    (node as BranchNode<T>).value
                } else {
                    val firstChar = hexCharToIndex(key[0])
                    getRecursive(node.children[firstChar], key.substring(1))
                }
            }
        }
    }

    private fun removeRecursive(node: MPTNode?, key: String): MPTNode? {
        // Simplified removal of n element
        // TODO: IMPROVE THIS IMPLEMENTATION TO INCLUDE THE TRIE REORGANIZATION
        return node
    }

    private fun collectProofNodes(node: MPTNode?, key: String, proof: MutableList<ByteArray>) {
        if (node == null) return

        proof.add(node.serialize())

        when (node) {
            is LeafNode<*> -> {
                // Leaf node reached
            }

            is ExtensionNode -> {
                if (key.startsWith(node.sharedKey)) {
                    collectProofNodes(node.nextNode, key.substring(node.sharedKey.length), proof)
                }
            }

            is BranchNode<*> -> {
                if (key.isNotEmpty()) {
                    val firstChar = hexCharToIndex(key[0])
                    collectProofNodes(node.children[firstChar], key.substring(1), proof)
                }
            }
        }
    }

    private fun collectKeys(node: MPTNode?, prefix: String, keys: MutableList<String>) {
        if (node == null) return

        when (node) {
            is LeafNode<*> -> {
                keys.add(prefix + node.keyEnd)
            }

            is ExtensionNode -> {
                collectKeys(node.nextNode, prefix + node.sharedKey, keys)
            }

            is BranchNode<*> -> {
                // If the branch has a value, add the current key
                if (node.value != null) {
                    keys.add(prefix)
                }

                // Children recursion
                for (i in 0..15) {
                    if (node.children[i] != null) {
                        collectKeys(node.children[i], prefix + indexToHexChar(i), keys)
                    }
                }
            }
        }
    }

    private fun getCommonPrefix(str1: String, str2: String): String {
        val minLength = minOf(str1.length, str2.length)
        for (i in 0 until minLength) {
            if (str1[i] != str2[i]) {
                return str1.substring(0, i)
            }
        }
        return str1.substring(0, minLength)
    }

    private fun hexCharToIndex(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            in 'A'..'F' -> char - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $char")
        }
    }

    private fun indexToHexChar(index: Int): Char {
        return if (index < 10) {
            ('0' + index)
        } else {
            ('a' + index - 10)
        }
    }
}
