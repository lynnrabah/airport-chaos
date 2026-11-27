
import java.util.ArrayList;

public class RunwayBST {

    // Node class
    private static class Node {
        Node right;
        Node left;
        Flight flight;

        public Node(Flight flight, Node left, Node right) {
            this.flight = flight;
            this.left = left;
            this.right = right;
        }

        public Node(Flight flight) {
            this(flight, null, null);
        }

        public Node() {
            this(null);
        }
    }

    private Node root;
    private boolean trace;

    // getRoot
    public Node getRoot() {
        return root;
    }

    // constructors
    public RunwayBST(Node root) {
        this.root = root;
    }

    public RunwayBST(Flight flight) {
        this(new Node(flight));
    }

    public RunwayBST() {
        root = null;
    }

    // used in other methods
    private int compare(Flight a, Flight b) {
        int result = Long.compare(a.start, b.start);

        if (result != 0)
            return result;

        return a.flightId.compareTo(b.flightId);
    }

    // Compare a key with a flight
    private int compare(long start, String flightId, Flight flight) {
        int result = Long.compare(start, flight.start);

        if (result != 0)
            return result;

        return flightId.compareTo(flight.flightId);
    }

    // searching for a flight
    public Node search(Flight flight) {
        Node current = root;

        while (current != null) {
            int result = compare(flight, current.flight);

            if (result > 0)
                current = current.right;
            else if (result < 0)
                current = current.left;
            else
                return current;
        }

        return null;
    }

    // inserting a flight
    private void insertNode(Node newNode) {

        if (root == null) {
            root = newNode;
            return;
        }

        Node currentRoot = root;
        Node parent = null;

        while (currentRoot != null) {
            parent = currentRoot;

            int result = compare(newNode.flight, currentRoot.flight);

            if (result > 0)
                currentRoot = currentRoot.right;
            else if (result < 0)
                currentRoot = currentRoot.left;
            else
                throw new IllegalArgumentException("Duplicate flight key");
        }

        if (compare(newNode.flight, parent.flight) > 0)
            parent.right = newNode;
        else
            parent.left = newNode;
    }

    public void insert(Flight flight) {

        Node newNode = new Node(flight);

        if (findConflicts(flight))
            throw new IllegalArgumentException("flight conflicts with another one");

        insertNode(newNode);
    }

    // deleting a flight
    public Node delete(Node root, Flight flight) {

        if (root == null)
            return null;

        int result = compare(flight, root.flight);

        if (result > 0)
            root.right = delete(root.right, flight);

        else if (result < 0)
            root.left = delete(root.left, flight);

        else {
            // no children
            if (root.left == null && root.right == null)
                return null;

            // one child
            if (root.left == null)
                return root.right;

            if (root.right == null)
                return root.left;

            // two children
            Node successor = successorNode(root);
            root.flight = successor.flight;
            root.right = delete(root.right, successor.flight);
        }

        return root;
    }

    public boolean delete(Flight flight) {

        if (search(flight) == null)
            return false;

        root = delete(root, flight);

        return true;
    }

    // Delete using start time and flight ID
    private Node deleteNode(Node node, long start, String flightId) {

        if (node == null)
            return null;

        int result = compare(start, flightId, node.flight);

        if (result > 0)
            node.right = deleteNode(node.right, start, flightId);

        else if (result < 0)
            node.left = deleteNode(node.left, start, flightId);

        else {
            // no children
            if (node.left == null && node.right == null)
                return null;

            // one child
            if (node.left == null)
                return node.right;

            if (node.right == null)
                return node.left;

            // two children
            Node successor = successorNode(node);
            node.flight = successor.flight;
            node.right = deleteNode(node.right,
                    successor.flight.start,
                    successor.flight.flightId);
        }

        return node;
    }

    public boolean delete(long start, String flightId) {

        Node current = root;

        while (current != null) {

            int result = compare(start, flightId, current.flight);

            if (result > 0)
                current = current.right;

            else if (result < 0)
                current = current.left;

            else {
                root = deleteNode(root, start, flightId);
                return true;
            }
        }

        return false;
    }

    // find successor
    public Node successorNode(Node node) {

        if (node == null)
            return null;

        Node current = node.right;

        if (current != null) {
            while (current.left != null)
                current = current.left;

            return current;
        }

        // no right child
        current = this.root;
        Node successor = null;

        while (current != null) {
            if (compare(current.flight, node.flight) > 0) {
                successor = current;
                current = current.left;
            } else {
                current = current.right;
            }
        }

        return successor;
    }

    public Node successor(Flight flight) {
        Node node = search(flight);
        return successorNode(node);
    }

    // find predecessor
    public Node predecessorNode(Node node) {

        if (node == null)
            return null;

        Node current = node.left;

        if (current != null) {
            while (current.right != null)
                current = current.right;

            return current;
        }

        // no left child
        current = this.root;
        Node predecessor = null;

        while (current != null) {
            if (compare(current.flight, node.flight) < 0) {
                predecessor = current;
                current = current.right;
            } else {
                current = current.left;
            }
        }

        return predecessor;
    }

    public Node predecessor(Flight flight) {
        Node node = search(flight);
        return predecessorNode(node);
    }

    // inorder listing
    private void inorder(Node node, ArrayList<Flight> result) {

        if (node == null)
            return;

        inorder(node.left, result);

        result.add(node.flight);

        inorder(node.right, result);
    }

    public ArrayList<Flight> inorder() {

        ArrayList<Flight> result = new ArrayList<>();

        inorder(root, result);

        return result;
    }

    // Time-range search
    private void rangeSearch(Node node, long start, long end,
            ArrayList<Flight> result) {

        if (node == null)
            return;

        if (node.flight.start >= start)
            rangeSearch(node.left, start, end, result);

        if (node.flight.start >= start && node.flight.start < end)
            result.add(node.flight);

        if (node.flight.start < end)
            rangeSearch(node.right, start, end, result);
    }

    public ArrayList<Flight> rangeSearch(long start, long end) {

        if (start > end)
            throw new IllegalArgumentException("Invalid time range");

        ArrayList<Flight> result = new ArrayList<>();

        rangeSearch(root, start, end, result);

        return result;
    }

    // Find all flights overlapping a given interval
    private void findConflicts(Node node, long start, long end,
            ArrayList<Flight> result) {

        if (node == null)
            return;

        // Left subtree might contain flights that extend into the interval
        findConflicts(node.left, start, end, result);

        // Check current flight
        if (node.flight.start < end && node.flight.end > start)
            result.add(node.flight);

        // Right subtree can be skipped if current start >= end
        if (node.flight.start < end)
            findConflicts(node.right, start, end, result);
    }

    public ArrayList<Flight> findConflicts(long start, long end) {

        if (start >= end)
            throw new IllegalArgumentException("Invalid time interval");

        ArrayList<Flight> result = new ArrayList<>();

        findConflicts(root, start, end, result);

        return result;
    }

    // Check whether a flight conflicts with existing flights
    public boolean findConflicts(Flight flight) {

        return !findConflicts(flight.start, flight.end).isEmpty();
    }

    // Count nodes
    private int size(Node node) {

        if (node == null)
            return 0;

        return 1 + size(node.left) + size(node.right);
    }

    public int size() {
        return size(root);
    }

    // Calculate height
    private int height(Node node) {

        if (node == null)
            return 0;

        return 1 + Math.max(height(node.left), height(node.right));
    }

    public int height() {
        return height(root);
    }

    // Enable or disable tracing
    public void setTrace(boolean on) {
        trace = on;
    }

}
