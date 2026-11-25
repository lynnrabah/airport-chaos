
public class RunwaySchedulerBST {

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

    // getRoot
    public Node getRoot() {
        return root;
    }

    // constructors
    public RunwaySchedulerBST(Node root) {
        this.root = root;
    }

    public RunwaySchedulerBST(Flight flight) {
        this(new Node(flight));
    }

    public RunwaySchedulerBST() {
        root = null;
    }

    // used in other methods
    private int compare(Flight a, Flight b) {
        int result = Long.compare(a.start, b.start);

        if (result != 0)
            return result;

        return a.flightId.compareTo(b.flightId);
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
        if(checkConflict(flight))
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

    public void delete(Flight flight) {
        root = delete(root, flight);
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
    private void inorder(Node node) {
        if (node == null)
            return;

        inorder(node.left);
        System.out.println(node.flight);
        inorder(node.right);
    }

    public void inorderListing() {
        inorder(root);
    }

    // time-range search
    private void rangeSearch(Node node, long start, long end) {

        if (node == null)
            return;

        if (node.flight.start >= start)
            rangeSearch(node.left, start, end);

        // Check current flight
        if (node.flight.start >= start && node.flight.start <= end)
            System.out.println(node.flight);

        if (node.flight.start <= end)
            rangeSearch(node.right, start, end);
    }

    public void rangeSearch(long start, long end) {

        if (start > end)
            throw new IllegalArgumentException("Invalid time range");

        rangeSearch(root, start, end);
    }

    //check flight conflict time
    public boolean checkConflict(Flight flight) {

        Node predecessor = predecessor(flight);
        Node successor = successor(flight);

        if (predecessor != null &&
                predecessor.flight.end > flight.start)
            return true;

        if (successor != null &&
                successor.flight.start < flight.end)
            return true;

        return false;
    }

}
