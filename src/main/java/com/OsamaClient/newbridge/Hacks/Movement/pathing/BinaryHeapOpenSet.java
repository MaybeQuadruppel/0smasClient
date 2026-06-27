package com.OsamaClient.newbridge.Hacks.Movement.pathing;

/**
 * A binary min-heap for PathNode objects.
 * Supports O(log n) insert and decrease-key (update).
 * Modeled after Baritone's BinaryHeapOpenSet.
 */
public final class BinaryHeapOpenSet {

    private PathNode[] heap;
    private int size;

    public BinaryHeapOpenSet() {
        this.heap = new PathNode[1024];
        this.size = 0;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void insert(PathNode node) {
        if (size >= heap.length - 1) {
            PathNode[] newHeap = new PathNode[heap.length * 2];
            System.arraycopy(heap, 1, newHeap, 1, size);
            heap = newHeap;
        }
        size++;
        heap[size] = node;
        node.heapPosition = size;
        bubbleUp(size);
    }

    /**
     * Called when a node's cost has decreased — re-sort it upward.
     */
    public void update(PathNode node) {
        bubbleUp(node.heapPosition);
    }

    public PathNode removeLowest() {
        if (size == 0) throw new IllegalStateException("Heap is empty");
        PathNode result = heap[1];
        result.heapPosition = -1;
        heap[1] = heap[size];
        heap[size] = null;
        size--;
        if (size > 0) {
            heap[1].heapPosition = 1;
            pushDown(1);
        }
        return result;
    }

    private void bubbleUp(int pos) {
        PathNode node = heap[pos];
        while (pos > 1) {
            int parent = pos >> 1;
            if (heap[parent].combinedCost <= node.combinedCost) break;
            heap[pos] = heap[parent];
            heap[pos].heapPosition = pos;
            pos = parent;
        }
        heap[pos] = node;
        node.heapPosition = pos;
    }

    private void pushDown(int pos) {
        PathNode node = heap[pos];
        while (true) {
            int child = pos << 1;
            if (child > size) break;
            if (child + 1 <= size && heap[child + 1].combinedCost < heap[child].combinedCost) {
                child++;
            }
            if (node.combinedCost <= heap[child].combinedCost) break;
            heap[pos] = heap[child];
            heap[pos].heapPosition = pos;
            pos = child;
        }
        heap[pos] = node;
        node.heapPosition = pos;
    }
}
