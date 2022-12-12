/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util;// BinarySearchTree class
//
// CONSTRUCTION: with no initializer
//
// ******************PUBLIC OPERATIONS*********************
// void insert( x )       --> Insert x
// void remove( x )       --> Remove x
// void removeMin( )      --> Remove minimum item
// Comparable find( x )   --> Return item that matches x
// Comparable findMin( )  --> Return smallest item
// Comparable findMax( )  --> Return largest item
// boolean isEmpty( )     --> Return true if empty; else false
// void makeEmpty( )      --> Remove all items
// ******************ERRORS********************************
// Exceptions are thrown by insert, remove, and removeMin if warranted

/**
 * Implements an unbalanced binary search tree.
 * Note that all "matching" is based on the compareTo method.
 *
 * @author Mark Allen Weiss
 */
public class BinarySearchTree {
    /** The tree root. */
    protected BinaryNode root;

    /**
     * Construct the tree.
     */
    public BinarySearchTree() {
        root = null;
    }

    // Test program
    public static void main(String[] args) {
        BinarySearchTree t = new BinarySearchTree();
        final int NUMS = 4000;
        final int GAP = 37;

        System.out.println("Checking... (no more output means success)");

        for (int i = GAP; i != 0; i = (i + GAP) % NUMS)
            t.insert(i);

        for (int i = 1; i < NUMS; i += 2)
            t.remove(i);

        if ((Integer) (t.findMin()) != 2 || (Integer) (t.findMax()) != NUMS - 2)
            System.out.println("FindMin or FindMax error!");

        for (int i = 2; i < NUMS; i += 2)
            if ((Integer) (t.find(i)) != i)
                System.out.println("Find error1!");

        for (int i = 1; i < NUMS; i += 2) {
            if (t.find(i) != null)
                System.out.println("Find error2!");
        }
    }

    /**
     * Insert into the tree.
     *
     * @param x the item to insert.
     *
     * @throws RuntimeException if x is already present.
     */
    public void insert(Comparable x) {
        root = insert(x, root);
    }

    /**
     * Remove from the tree..
     *
     * @param x the item to remove.
     *
     * @throws RuntimeException if x is not found.
     */
    public void remove(Comparable x) {
        root = remove(x, root);
    }

    /**
     * Remove minimum item from the tree.
     *
     * @throws RuntimeException if tree is empty.
     */
    public void removeMin() {
        root = removeMin(root);
    }

    /**
     * Find the smallest item in the tree.
     *
     * @return smallest item or null if empty.
     */
    public Comparable findMin() {
        return elementAt(findMin(root));
    }

    /**
     * Find the largest item in the tree.
     *
     * @return the largest item or null if empty.
     */
    public Comparable findMax() {
        return elementAt(findMax(root));
    }

    /**
     * Find an item in the tree.
     *
     * @param x the item to search for.
     *
     * @return the matching item or null if not found.
     */
    public Comparable find(Comparable x) {
        return elementAt(find(x, root));
    }

    /**
     * Find the start of the interval where x is in.
     *
     * @param x is the item to search the start for.
     *
     * @return node containing the start of the interval where x
     * is in, or null if x is not contained in any interval.
     */
    public Comparable findIntervalStart(Comparable x) {
        return elementAt(findIntervalStart(x, root));
    }

    /**
     * Make the tree logically empty.
     */
    public void makeEmpty() {
        root = null;
    }

    /**
     * Test if the tree is logically empty.
     *
     * @return true if empty, false otherwise.
     */
    public boolean isEmpty() {
        return root == null;
    }

    /**
     * Internal method to get element field.
     *
     * @param t the node.
     *
     * @return the element field or null if t is null.
     */
    private Comparable elementAt(BinaryNode t) {
        return t == null ? null : t.element;
    }

    /**
     * Internal method to insert into a subtree.
     *
     * @param x the item to insert.
     * @param t the node that roots the tree.
     *
     * @return the new root.
     *
     * @throws RuntimeException if x is already present.
     */
    protected BinaryNode insert(Comparable x, BinaryNode t) {
        if (t == null)
            t = new BinaryNode(x);
        else if (x.compareTo(t.element) < 0)
            t.left = insert(x, t.left);
        else if (x.compareTo(t.element) > 0)
            t.right = insert(x, t.right);
        else
            throw new RuntimeException("Duplicate item: " + x.toString());  // Duplicate
        return t;
    }

    /**
     * Internal method to remove from a subtree.
     *
     * @param x the item to remove.
     * @param t the node that roots the tree.
     *
     * @return the new root.
     *
     * @throws RuntimeException if x is not found.
     */
    protected BinaryNode remove(Comparable x, BinaryNode t) {
        if (t == null)
            throw new RuntimeException("Item not found: " + x.toString());
        if (x.compareTo(t.element) < 0)
            t.left = remove(x, t.left);
        else if (x.compareTo(t.element) > 0)
            t.right = remove(x, t.right);
        else if (t.left != null && t.right != null) // Two children
        {
            t.element = findMin(t.right).element;
            t.right = removeMin(t.right);
        } else
            t = (t.left != null) ? t.left : t.right;
        return t;
    }

    /**
     * Internal method to remove minimum item from a subtree.
     *
     * @param t the node that roots the tree.
     *
     * @return the new root.
     *
     * @throws RuntimeException if x is not found.
     */
    protected BinaryNode removeMin(BinaryNode t) {
        if (t == null)
            throw new RuntimeException("Item not found");
        else if (t.left != null) {
            t.left = removeMin(t.left);
            return t;
        } else
            return t.right;
    }

    /**
     * Internal method to find the smallest item in a subtree.
     *
     * @param t the node that roots the tree.
     *
     * @return node containing the smallest item.
     */
    protected BinaryNode findMin(BinaryNode t) {
        if (t != null)
            while (t.left != null)
                t = t.left;

        return t;
    }

    /**
     * Internal method to find the largest item in a subtree.
     *
     * @param t the node that roots the tree.
     *
     * @return node containing the largest item.
     */
    private BinaryNode findMax(BinaryNode t) {
        if (t != null)
            while (t.right != null)
                t = t.right;

        return t;
    }

    /**
     * Internal method to find an item in a subtree.
     *
     * @param x is item to search for.
     * @param t the node that roots the tree.
     *
     * @return node containing the matched item.
     */
    private BinaryNode find(Comparable x, BinaryNode t) {
        while (t != null) {
            if (x.compareTo(t.element) < 0)
                t = t.left;
            else if (x.compareTo(t.element) > 0)
                t = t.right;
            else
                return t;    // Match
        }

        return null;         // Not found
    }

    /**
     * Internal method to find the interval start of x if the
     * items in the subtree are regarded as interval separations.
     *
     * @param x is the item to search the start for.
     * @param t the node that roots the tree.
     *
     * @return node containing the start of the interval where x
     * is in, or null if x is not contained in any interval.
     */
    private BinaryNode findIntervalStart(Comparable x, BinaryNode t) {
        BinaryNode lastPrev = null;
        while (t != null) {
            if (x.compareTo(t.element) < 0) {
                t = t.left;
            } else if (x.compareTo(t.element) > 0) {
                lastPrev = t;
                t = t.right;
            } else {
                return t;    // Match
            }
        }

        return lastPrev;         // The last node visited on the left
    }
}

// Basic node stored in unbalanced binary search trees
// Note that this class is not accessible outside
// of this package.

class BinaryNode {
    // Friendly data; accessible by other package routines
    Comparable element;      // The data in the node
    BinaryNode left;         // Left child
    BinaryNode right;        // Right child
    // Constructors
    BinaryNode(Comparable theElement) {
        element = theElement;
        left = right = null;
    }
}
 
